package uk.anbu.devnotes.controller

import gg.jte.ContentType
import gg.jte.TemplateEngine
import gg.jte.resolve.DirectoryCodeResolver
import org.commonmark.node.Text
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.http.HttpStatus
import spock.lang.Specification
import uk.anbu.devnotes.module.MarkdownRenderer
import uk.anbu.devnotes.service.ConfigService
import uk.anbu.devnotes.service.DataSourceConfig

import java.nio.file.Files
import java.nio.file.Paths

class MarkdownControllerSpec extends Specification {

    MarkdownController controller
    MarkdownRenderer markdownRenderer
    TemplateEngine templateEngine
    ConfigService configService
    def sqlToJsonFileResolver, sqlToHtmlTableResolver, groovyCodeBlockResolver, dataSourceConfigResolver

    def setup() {
        sqlToJsonFileResolver = x -> Paths.get("src/test/resources/sql-result.json")
        sqlToHtmlTableResolver = x -> "html-table"
        groovyCodeBlockResolver = x -> new Text("groovy-code-block")
        dataSourceConfigResolver = x -> new DataSourceConfig("testDB", "jdbc:test:url", "testUser", "testPass", "org.test.Driver")
        markdownRenderer = new MarkdownRenderer(sqlToJsonFileResolver,
                sqlToHtmlTableResolver,
                groovyCodeBlockResolver,
                dataSourceConfigResolver)
        var codeResolver = new DirectoryCodeResolver(Paths.get("src/main/jte"))
        templateEngine =  TemplateEngine.create(codeResolver, Paths.get("src/main/jte"), ContentType.Html)
        configService = Mock(ConfigService)
        controller = new MarkdownController(markdownRenderer, templateEngine, configService)
    }

    def "markdown() should redirect to index.md when filename is null"() {
        when:
        def response = controller.markdown(null, false)

        then:
        response.statusCode == HttpStatus.FOUND
        response.headers.getFirst("Location") == "/markdown?filename=index.md"
    }

    def "markdown() should handle directory"() {
        given:
        def tempDir = Files.createTempDirectory("test")
        configService.getDocsDirectory() >> tempDir.toFile().parentFile.absolutePath

        when:
        def response = controller.markdown(tempDir.fileName.toString(), false)

        then:
        response.statusCode == HttpStatus.FOUND
        response.headers.getFirst("Location") == "/renderDirectoryContents?directoryName=" + tempDir.fileName.toString()

        cleanup:
        tempDir.deleteDir()
    }

    def "markdown() should handle missing markdown file"() {
        given:
        def tempFile = Files.createTempFile("test", ".md")
        configService.getDocsDirectory() >> tempFile.toFile().parentFile.absolutePath

        when:
        def response = controller.markdown("non-existent.md", false)

        then:
        Document doc = Jsoup.parse(response.body.toString())
        response.statusCode == HttpStatus.OK
        doc.select("body > h1").text() == "File Not Found"
        doc.select("body > p:nth-of-type(1)").text() == "The requested markdown file \"non-existent.md\" was not found."
        doc.select("body > p:nth-of-type(2)").text() == "Would you like to create it?"
    }

    def "markdown() should render markdown content"() {
        given:
        def tempFile = Files.createTempFile("test", ".md")
        configService.getDocsDirectory() >> tempFile.toFile().parentFile.absolutePath
        Files.write(tempFile, "# Test".getBytes())

        when:
        def response = controller.markdown(tempFile.fileName.toString(), false)

        then:
        Document doc = Jsoup.parse(response.body.toString())
        response.statusCode == HttpStatus.OK
        doc.select("h1").text() == "Test"

        cleanup:
        Files.deleteIfExists(tempFile)
    }

    def "markdown() should interpret image location as relative to current document"() {
        given:
        def tempDirectory = Files.createTempDirectory("test").toFile()
        new File(tempDirectory, "nested1/nested2").mkdirs()
        var newFile = new File(tempDirectory, "nested1/nested2/new-file.md")

        Files.write(newFile.toPath(), """# Test\n![image](image.png)""".getBytes())
        configService.getDocsDirectory() >> tempDirectory.absolutePath

        when:
        def response = controller.markdown("nested1/nested2/new-file.md", false)

        then:
        Document doc = Jsoup.parse(response.body.toString())
        response.statusCode == HttpStatus.OK
        doc.select("h1").text() == "Test"
        doc.select("h1 + p > img").get(0).attr("src").endsWith("filename=nested1/nested2/image.png")

        cleanup:
        tempDirectory.deleteDir()
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
        tempDir.deleteDir()
    }

    def "saveMarkdown() should save markdown content"() {
        given:
        def tempFile = Files.createTempFile("test", ".md")
        def content = "# New content"
        configService.getDocsDirectory() >> tempFile.toFile().parentFile.absolutePath
        Files.write(tempFile, content.getBytes())

        when:
        def response = controller.saveMarkdown(tempFile.fileName.toString(), false, content)

        then:
        response.statusCode == HttpStatus.FOUND
        response.headers.getFirst("Location") == "/markdown?filename=" + tempFile.fileName.toString() + "&edit=false"
        Files.readString(tempFile) == content

        cleanup:
        Files.deleteIfExists(tempFile)
    }

    def "Markdown with a sql error should render the error message"() {
        given:
        def tempFile = Files.createTempFile("test", ".md")
        configService.getDocsDirectory() >> tempFile.toFile().parentFile.absolutePath
        Files.write(tempFile, "# Test\n```sql(missing-database)\nselect * from user\n```".getBytes())

        when:
        def response = controller.markdown(tempFile.fileName.toString(), false)

        then:
        Document doc = Jsoup.parse(response.body.toString())
        response.statusCode == HttpStatus.OK
        doc.select("h1").text() == "Test"
        doc.select("h1 + pre > code").text() == "select * from user"
        doc.select("h1 + pre > code").get(0).attr("class") == "language-hidden-sql"
        // TODO: check for error message

        cleanup:
        Files.deleteIfExists(tempFile)
    }
}