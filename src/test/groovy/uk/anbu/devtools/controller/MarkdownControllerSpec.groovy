package uk.anbu.devtools.controller

import gg.jte.ContentType
import gg.jte.TemplateEngine
import gg.jte.resolve.ResourceCodeResolver
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.http.HttpStatus
import spock.lang.Specification
import uk.anbu.devtools.module.MarkdownRenderer
import uk.anbu.devtools.service.ConfigService

import java.nio.file.Files
import java.nio.file.Paths

class MarkdownControllerSpec extends Specification {

    MarkdownController controller
    MarkdownRenderer markdownRenderer
    TemplateEngine templateEngine
    ConfigService configService

    def setup() {
        markdownRenderer = new MarkdownRenderer()
        var codeResolver = new ResourceCodeResolver("templates")
        templateEngine =  TemplateEngine.create(codeResolver, Paths.get("src/main/resources/templates"), ContentType.Html)
        configService = Mock(ConfigService)
        controller = new MarkdownController(markdownRenderer, templateEngine, configService)
    }

    def "markdown() should redirect to index.md when filename is null"() {
        when:
        def response = controller.markdown(null)

        then:
        response.statusCode == HttpStatus.FOUND
        response.headers.getFirst("Location") == "/markdown?filename=index.md"
    }

    def "markdown() should handle directory"() {
        given:
        def tempDir = Files.createTempDirectory("test")
        configService.getDocsDirectory() >> tempDir.toFile().parentFile.absolutePath

        when:
        def response = controller.markdown(tempDir.fileName.toString())

        then:
        response.statusCode == HttpStatus.FOUND
        response.headers.getFirst("Location") == "/renderDirectoryContents?directoryName=" + tempDir.fileName.toString()

        cleanup:
        Files.deleteIfExists(tempDir)
    }

    def "markdown() should handle missing markdown file"() {
        given:
        def tempFile = Files.createTempFile("test", ".md")
        configService.getDocsDirectory() >> tempFile.toFile().parentFile.absolutePath

        when:
        def response = controller.markdown("non-existent.md")

        then:
        response.statusCode == HttpStatus.OK
        response.body =~ ".*?The requested markdown file \"non-existent.md\" was not found.*?"
    }

    def "markdown() should render markdown content"() {
        given:
        def tempFile = Files.createTempFile("test", ".md")
        configService.getDocsDirectory() >> tempFile.toFile().parentFile.absolutePath
        Files.write(tempFile, "# Test".getBytes())

        when:
        def response = controller.markdown(tempFile.fileName.toString())

        then:
        Document doc = Jsoup.parse(response.body.toString())
        response.statusCode == HttpStatus.OK
        doc.select("h1").text() == "Test"
        doc.select("TextArea#editor").text() == "# Test"

        cleanup:
        Files.deleteIfExists(tempFile)
    }

    def "createNewMarkdown() should create a new markdown file"() {
        given:
        def tempDir = Files.createTempDirectory("test")
        def filename = "new-file.md"
        configService.getDocsDirectory() >> tempDir.toFile().absolutePath

        when:
        def response = controller.createNewMarkdown(filename)

        then:
        response.statusCode == HttpStatus.FOUND
        response.headers.getFirst("Location") == "/markdown?filename=" + filename
        Files.exists(tempDir.resolve(filename))

        cleanup:
        Files.deleteIfExists(tempDir.resolve(filename))
        Files.deleteIfExists(tempDir)
    }

    def "saveMarkdown() should save markdown content"() {
        given:
        def tempFile = Files.createTempFile("test", ".md")
        def content = "# New content"
        configService.getDocsDirectory() >> tempFile.toFile().parentFile.absolutePath
        Files.write(tempFile, content.getBytes())

        when:
        def response = controller.saveMarkdown(tempFile.fileName.toString(), content)

        then:
        response.statusCode == HttpStatus.OK
        response.body == "Saved successfully"
        Files.readString(tempFile) == content

        cleanup:
        Files.deleteIfExists(tempFile)
    }
}