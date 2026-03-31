package uk.anbu.devnotes.controller

import gg.jte.ContentType
import gg.jte.TemplateEngine
import gg.jte.TemplateOutput
import gg.jte.resolve.DirectoryCodeResolver
import org.jsoup.Jsoup
import org.springframework.http.HttpStatus
import spock.lang.Specification
import uk.anbu.devnotes.service.ConfigService

import java.nio.file.Files
import java.nio.file.Paths

class MermaidPlaygroundControllerSpec extends Specification {

    MermaidPlaygroundController controller
    TemplateEngine templateEngine
    ConfigService configService

    def setup() {
        def codeResolver = new DirectoryCodeResolver(Paths.get("src/main/jte"))
        templateEngine = TemplateEngine.create(codeResolver, Paths.get("src/main/jte"), ContentType.Html)
        configService = Mock(ConfigService)
        controller = new MermaidPlaygroundController(templateEngine, configService)
    }

    def "mermaidPlayground() should render the playground template with default content when no saved file exists"() {
        given:
        def tempDir = Files.createTempDirectory("test")
        configService.getDocsDirectory() >> tempDir.toFile().absolutePath

        when:
        def response = controller.mermaidPlayground()

        then:
        response.statusCode == HttpStatus.OK
        response.headers.getContentType().toString().contains("text/html")
        def doc = Jsoup.parse(response.body.toString())
        doc.select("title").text() == "Mermaid playground"
        doc.select(".container").size() == 1
        doc.select("#editor").val().contains("graph TD")

        cleanup:
        tempDir.toFile().deleteDir()
    }

    def "mermaidPlayground() should load previously saved content from disk"() {
        given:
        def tempDir = Files.createTempDirectory("test")
        def savedContent = "flowchart LR\n    A --> B"
        def autosavePath = tempDir.resolve("config/mermaid/last-edited.mmd")
        Files.createDirectories(autosavePath.parent)
        Files.writeString(autosavePath, savedContent)
        configService.getDocsDirectory() >> tempDir.toFile().absolutePath

        when:
        def response = controller.mermaidPlayground()

        then:
        response.statusCode == HttpStatus.OK
        def doc = Jsoup.parse(response.body.toString())
        doc.select("#editor").val() == savedContent

        cleanup:
        tempDir.toFile().deleteDir()
    }

    def "autosave() should write content to config/mermaid/last-edited.mmd"() {
        given:
        def tempDir = Files.createTempDirectory("test")
        configService.getDocsDirectory() >> tempDir.toFile().absolutePath
        def content = "graph LR\n    X --> Y"

        when:
        def response = controller.autosave(content)

        then:
        response.statusCode == HttpStatus.OK
        def savedPath = tempDir.resolve("config/mermaid/last-edited.mmd")
        Files.exists(savedPath)
        Files.readString(savedPath) == content

        cleanup:
        tempDir.toFile().deleteDir()
    }

    def "autosave() should create parent directories when they do not exist"() {
        given:
        def tempDir = Files.createTempDirectory("test")
        configService.getDocsDirectory() >> tempDir.toFile().absolutePath

        when:
        def response = controller.autosave("sequenceDiagram\n    A->>B: Hello")

        then:
        response.statusCode == HttpStatus.OK
        Files.isDirectory(tempDir.resolve("config/mermaid"))

        cleanup:
        tempDir.toFile().deleteDir()
    }

    def "autosave() should overwrite a previously saved file"() {
        given:
        def tempDir = Files.createTempDirectory("test")
        configService.getDocsDirectory() >> tempDir.toFile().absolutePath
        def autosavePath = tempDir.resolve("config/mermaid/last-edited.mmd")
        Files.createDirectories(autosavePath.parent)
        Files.writeString(autosavePath, "old content")

        when:
        def response = controller.autosave("new content")

        then:
        response.statusCode == HttpStatus.OK
        Files.readString(autosavePath) == "new content"

        cleanup:
        tempDir.toFile().deleteDir()
    }

    def "mermaidPlayground() should surface template errors"() {        given:
        def tempDir = Files.createTempDirectory("test")
        configService.getDocsDirectory() >> tempDir.toFile().absolutePath
        def failingEngine = Mock(TemplateEngine)
        failingEngine.render(_ as String, _ as Map, _ as TemplateOutput) >> {
            throw new IllegalStateException("boom")
        }
        def failingController = new MermaidPlaygroundController(failingEngine, configService)

        when:
        def response = failingController.mermaidPlayground()

        then:
        response.statusCode == HttpStatus.INTERNAL_SERVER_ERROR
        response.body.toString().contains("Error rendering mermaid playground: boom")

        cleanup:
        tempDir.toFile().deleteDir()
    }

    def "save() should write content to the specified relative path within the docs directory"() {
        given:
        def tempDir = Files.createTempDirectory("test")
        configService.getDocsDirectory() >> tempDir.toFile().absolutePath
        def content = "sequenceDiagram\n    Alice->>Bob: Hello"

        when:
        def response = controller.save("diagrams/my-chart.mmd", content)

        then:
        response.statusCode == HttpStatus.OK
        def savedPath = tempDir.resolve("diagrams/my-chart.mmd")
        Files.exists(savedPath)
        Files.readString(savedPath) == content

        cleanup:
        tempDir.toFile().deleteDir()
    }

    def "save() should create intermediate directories when they do not exist"() {
        given:
        def tempDir = Files.createTempDirectory("test")
        configService.getDocsDirectory() >> tempDir.toFile().absolutePath

        when:
        def response = controller.save("a/b/c/diagram.mmd", "graph LR\n    A --> B")

        then:
        response.statusCode == HttpStatus.OK
        Files.isDirectory(tempDir.resolve("a/b/c"))

        cleanup:
        tempDir.toFile().deleteDir()
    }

    def "save() should reject path traversal attempts"() {
        given:
        def tempDir = Files.createTempDirectory("test")
        configService.getDocsDirectory() >> tempDir.toFile().absolutePath

        when:
        def response = controller.save("../../etc/passwd", "evil content")

        then:
        response.statusCode == HttpStatus.BAD_REQUEST

        cleanup:
        tempDir.toFile().deleteDir()
    }
}