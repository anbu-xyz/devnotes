package uk.anbu.devnotes.controller

import gg.jte.ContentType
import gg.jte.TemplateEngine
import gg.jte.resolve.DirectoryCodeResolver
import org.springframework.http.HttpStatus
import spock.lang.Specification
import uk.anbu.devnotes.service.ConfigService
import uk.anbu.devnotes.service.ImageAuditService

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class ImageAuditControllerSpec extends Specification {

    Path tempDir
    ImageAuditController controller
    ImageAuditService imageAuditService
    ConfigService mockConfig

    def setup() {
        tempDir = Files.createTempDirectory("imageaudit")
        mockConfig = Mock(ConfigService)
        mockConfig.getDocsDirectory() >> tempDir.toString()

        imageAuditService = new ImageAuditService(mockConfig)

        def codeResolver = new DirectoryCodeResolver(Paths.get("src/main/jte"))
        def templateEngine = TemplateEngine.create(codeResolver, Paths.get("src/main/jte"), ContentType.Html)
        controller = new ImageAuditController(imageAuditService, templateEngine)
    }

    def cleanup() {
        tempDir.toFile().deleteDir()
    }

    // -------------------------------------------------------------------------
    // Service-level tests
    // -------------------------------------------------------------------------

    def "audit returns empty lists when docs directory contains no markdown files or images"() {
        when:
        def result = imageAuditService.audit()

        then:
        result.orphanedImages().isEmpty()
        result.brokenLinks().isEmpty()
    }

    def "audit detects an orphaned image (image on disk not linked from any markdown)"() {
        given:
        Files.createFile(tempDir.resolve("unused.png"))

        when:
        def result = imageAuditService.audit()

        then:
        result.orphanedImages() == ["unused.png"]
        result.brokenLinks().isEmpty()
    }

    def "audit detects a broken image link (markdown references missing image)"() {
        given:
        tempDir.resolve("notes.md").toFile().text = "# Test\n![missing](missing.png)"

        when:
        def result = imageAuditService.audit()

        then:
        result.brokenLinks().size() == 1
        result.brokenLinks()[0].markdownFile() == "notes.md"
        result.brokenLinks()[0].imageLink() == "missing.png"
        result.orphanedImages().isEmpty()
    }

    def "audit does not flag an image that is referenced by a markdown file"() {
        given:
        Files.createFile(tempDir.resolve("used.png"))
        tempDir.resolve("notes.md").toFile().text = "# Test\n![image](used.png)"

        when:
        def result = imageAuditService.audit()

        then:
        result.orphanedImages().isEmpty()
        result.brokenLinks().isEmpty()
    }

    def "audit resolves absolute image paths (starting with /) relative to docsDir"() {
        given:
        def imgDir = Files.createDirectory(tempDir.resolve("imgs"))
        Files.createFile(imgDir.resolve("logo.png"))
        tempDir.resolve("page.md").toFile().text = "# Page\n![logo](/imgs/logo.png)"

        when:
        def result = imageAuditService.audit()

        then:
        result.orphanedImages().isEmpty()
        result.brokenLinks().isEmpty()
    }

    def "audit resolves relative image paths in subdirectories"() {
        given:
        def subDir = Files.createDirectory(tempDir.resolve("sub"))
        Files.createFile(subDir.resolve("chart.png"))
        subDir.resolve("notes.md").toFile().text = "# Sub\n![chart](chart.png)"

        when:
        def result = imageAuditService.audit()

        then:
        result.orphanedImages().isEmpty()
        result.brokenLinks().isEmpty()
    }

    def "audit ignores external http/https image links"() {
        given:
        tempDir.resolve("page.md").toFile().text = "# Page\n![ext](https://example.com/img.png)"

        when:
        def result = imageAuditService.audit()

        then:
        result.orphanedImages().isEmpty()
        result.brokenLinks().isEmpty()
    }

    def "audit handles multiple markdown files and multiple images correctly"() {
        given:
        Files.createFile(tempDir.resolve("a.png"))   // referenced
        Files.createFile(tempDir.resolve("b.png"))   // orphaned
        tempDir.resolve("doc1.md").toFile().text = "![a](a.png)"
        tempDir.resolve("doc2.md").toFile().text = "![missing](missing.jpg)"  // broken

        when:
        def result = imageAuditService.audit()

        then:
        result.orphanedImages() == ["b.png"]
        result.brokenLinks().size() == 1
        result.brokenLinks()[0].markdownFile() == "doc2.md"
        result.brokenLinks()[0].imageLink() == "missing.jpg"
    }

    // -------------------------------------------------------------------------
    // Controller-level tests
    // -------------------------------------------------------------------------

    def "GET /tools/image-audit returns 200 with HTML body"() {
        when:
        def response = controller.imageAuditPage()

        then:
        response.statusCode == HttpStatus.OK
        response.body.contains("Image Audit")
    }

    def "GET /tools/image-audit shows orphaned image in the response"() {
        given:
        Files.createFile(tempDir.resolve("stale.gif"))

        when:
        def response = controller.imageAuditPage()

        then:
        response.statusCode == HttpStatus.OK
        response.body.contains("stale.gif")
    }

    def "GET /tools/image-audit shows broken link in the response"() {
        given:
        tempDir.resolve("doc.md").toFile().text = "![broken](nowhere.png)"

        when:
        def response = controller.imageAuditPage()

        then:
        response.statusCode == HttpStatus.OK
        response.body.contains("nowhere.png")
        response.body.contains("doc.md")
    }

    // -------------------------------------------------------------------------
    // DELETE endpoint tests
    // -------------------------------------------------------------------------

    def "POST /tools/image-audit/delete removes the image file and returns 200"() {
        given:
        Files.createFile(tempDir.resolve("orphan.png"))

        when:
        def response = controller.deleteOrphanedImage("orphan.png")

        then:
        response.statusCode == HttpStatus.OK
        !Files.exists(tempDir.resolve("orphan.png"))
    }

    def "POST /tools/image-audit/delete removes an image in a subdirectory"() {
        given:
        def sub = Files.createDirectory(tempDir.resolve("subdir"))
        Files.createFile(sub.resolve("unused.jpg"))

        when:
        def response = controller.deleteOrphanedImage("subdir/unused.jpg")

        then:
        response.statusCode == HttpStatus.OK
        !Files.exists(sub.resolve("unused.jpg"))
    }

    def "POST /tools/image-audit/delete returns 400 for a path traversal attempt"() {
        when:
        def response = controller.deleteOrphanedImage("../../etc/passwd")

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
    }

    def "POST /tools/image-audit/delete returns 400 for a non-image file"() {
        given:
        Files.createFile(tempDir.resolve("script.sh"))

        when:
        def response = controller.deleteOrphanedImage("script.sh")

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
    }

    def "POST /tools/image-audit/delete returns 400 for a blank path"() {
        when:
        def response = controller.deleteOrphanedImage("   ")

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
    }

    def "POST /tools/image-audit/delete returns 500 when file does not exist"() {
        when:
        def response = controller.deleteOrphanedImage("nonexistent.png")

        then:
        response.statusCode == HttpStatus.INTERNAL_SERVER_ERROR
    }
}

