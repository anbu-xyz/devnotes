package uk.anbu.devnotes.controller

import gg.jte.ContentType
import gg.jte.TemplateEngine
import gg.jte.resolve.DirectoryCodeResolver
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockMultipartFile
import spock.lang.Specification
import spock.lang.TempDir
import uk.anbu.devnotes.service.ConfigService

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class DirectoryListingControllerSpec extends Specification {

    @TempDir
    Path tempDir

    DirectoryListingController controller

    void setup() {
        ConfigService configService = Mock(ConfigService)
        configService.getDocsDirectory() >> tempDir.toString()
        controller = new DirectoryListingController(configService, createTemplateEngine())
    }

    void "GET / redirects to renderDirectoryContents"() {
        when:
        def response = controller.index()

        then:
        response.getStatusCode() == HttpStatus.FOUND
        response.headers.getFirst(HttpHeaders.LOCATION) == "/renderDirectoryContents?directoryName=."
    }

    void "POST /createSubdirectory creates folder"() {
        when:
        def response = controller.createSubdirectory(".", "notes")

        then:
        response.statusCode == HttpStatus.OK
        Files.isDirectory(tempDir.resolve("notes"))
    }

    void "POST /createMarkdown creates a file"() {
        when:
        def response = controller.createMarkdown(".", "note.md")

        then:
        response.statusCode == HttpStatus.OK
        Files.isRegularFile(tempDir.resolve("note.md"))
    }

    void "POST /renameEntry renames an existing file"() {
        given:
        Files.writeString(tempDir.resolve("old.md"), "content")

        when:
        def response = controller.renameEntry(".", "old.md", "renamed.md")

        then:
        response.statusCode == HttpStatus.OK
        Files.exists(tempDir.resolve("renamed.md"))
        !Files.exists(tempDir.resolve("old.md"))
    }

    void "POST /copyFileEntry duplicates a file"() {
        given:
        Files.writeString(tempDir.resolve("source.md"), "duplicate this")

        when:
        def response = controller.copyFileEntry(".", "source.md", "copy.md")

        then:
        response.statusCode == HttpStatus.OK
        Files.isRegularFile(tempDir.resolve("copy.md"))
        Files.readString(tempDir.resolve("copy.md")) == "duplicate this"
    }

    void "POST /copyFileEntry rejects directories"() {
        given:
        Files.createDirectories(tempDir.resolve("folder"))

        when:
        def response = controller.copyFileEntry(".", "folder", "folder-copy")

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
        response.body.contains("Copying directories is not supported")
    }

    void "POST /deleteEntry removes a file"() {
        given:
        Files.writeString(tempDir.resolve("garbage.txt"), "remove me")

        when:
        def response = controller.deleteEntry(".", "garbage.txt")

        then:
        response.statusCode == HttpStatus.OK
        !Files.exists(tempDir.resolve("garbage.txt"))
    }

    void "POST /deleteEntry removes directories recursively"() {
        given:
        Path folder = tempDir.resolve("outer/inner")
        Files.createDirectories(folder)
        Files.writeString(folder.resolve("note.md"), "ok")

        when:
        def response = controller.deleteEntry("outer", "inner")

        then:
        response.statusCode == HttpStatus.OK
        !Files.exists(folder)
    }

    def "GET /renderDirectoryContents shows entries"() throws IOException {
        given:
        Files.writeString(tempDir.resolve("file.md"), "content")
        Files.createDirectories(tempDir.resolve("subdir"))
        def expectedTitle = tempDir.getFileName().toString() + "/"

        when:
        def response = controller.renderDirectoryContents(".")

        then:
        response.statusCode == HttpStatus.OK
        response.body.contains("<title>${expectedTitle}</title>")
        response.body.contains("file.md")
        response.body.contains("subdir")
    }

    void "POST /uploadFile stores the multipart payload"() {
        given:
        Files.createDirectories(tempDir.resolve("uploads"))
        def file = new MockMultipartFile("file", "capture.txt", "text/plain", "payload".bytes)

        when:
        def response = controller.uploadFile(file, "uploads")

        then:
        response.statusCode == HttpStatus.OK
        Files.readString(tempDir.resolve("uploads/capture.txt")) == "payload"
    }

    private static TemplateEngine createTemplateEngine() {
        def resolver = new DirectoryCodeResolver(Paths.get("src/main/jte"))
        return TemplateEngine.create(resolver, Paths.get("src/main/jte"), ContentType.Html)
    }
}