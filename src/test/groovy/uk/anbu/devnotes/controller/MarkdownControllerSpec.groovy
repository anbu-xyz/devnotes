package uk.anbu.devnotes.controller

import gg.jte.ContentType
import gg.jte.TemplateEngine
import gg.jte.TemplateOutput
import gg.jte.resolve.DirectoryCodeResolver
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.http.HttpStatus
import spock.lang.Specification
import uk.anbu.devnotes.module.GroovyRenderer
import uk.anbu.devnotes.module.MarkdownRenderer
import uk.anbu.devnotes.markdown.code.DatabaseMetadataBlockTranslator
import uk.anbu.devnotes.module.sql.SqlExecutor
import uk.anbu.devnotes.service.ConfigService

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MarkdownControllerSpec extends Specification {

    MarkdownController controller
    MarkdownRenderer markdownRenderer
    TemplateEngine templateEngine
    ConfigService configService
    def sqlExecutor, groovyRenderer, dataSourceConfigResolver

    def setup() {
        sqlExecutor = Mock(SqlExecutor)
        sqlExecutor.renderResultAsJsonFile(_ as SqlExecutor.JsonGenerationRequest) >> Paths.get("src/test/resources/sql-result.json")
        sqlExecutor.convertToHtmlTable(_ as SqlExecutor.HtmlTableRequest) >> "html-table"
        groovyRenderer = new GroovyRenderer((r) -> Optional.empty())
        dataSourceConfigResolver = x -> new ConfigService.DataSourceConfig("testDB", "jdbc:test:url", "testUser", "testPass", "org.test.Driver")
        markdownRenderer = new MarkdownRenderer(groovyRenderer, dataSourceConfigResolver, sqlExecutor, configService, new DatabaseMetadataBlockTranslator(), null)
        var codeResolver = new DirectoryCodeResolver(Paths.get("src/main/jte"))
        templateEngine = TemplateEngine.create(codeResolver, Paths.get("src/main/jte"), ContentType.Html)
        configService = Mock(ConfigService)
        controller = new MarkdownController(markdownRenderer, templateEngine, configService)
    }

    def "markdown() should redirect to index.md when filename is null"() {
        when:
        def response = controller.markdown(null, Map.of(), false)

        then:
        response.statusCode == HttpStatus.FOUND
        response.headers.getFirst("Location") == "/markdown?filename=index.md"
    }

    def "markdown() should handle directory"() {
        given:
        def tempDir = Files.createTempDirectory("test")
        configService.getDocsDirectory() >> tempDir.toFile().parentFile.absolutePath

        when:
        def response = controller.markdown(tempDir.fileName.toString(), Map.of(), false)

        then:
        response.statusCode == HttpStatus.FOUND
        response.headers.getFirst("Location") == "/renderDirectoryContents?directoryName=" + tempDir.fileName.toString()

        cleanup:
        tempDir.toFile().deleteDir()
    }

    def "markdown() should handle missing markdown file"() {
        given:
        def tempFile = Files.createTempFile("test", ".md")
        configService.getDocsDirectory() >> tempFile.toFile().parentFile.absolutePath

        when:
        def response = controller.markdown("non-existent.md", Map.of(), false)

        then:
        Document doc = Jsoup.parse(response.body.toString())
        response.statusCode == HttpStatus.OK
        doc.select("body > h1").text() == "File Not Found"
        doc.select("body > p:nth-of-type(1)").text() == "The requested markdown file \"non-existent.md\" was not found."
        doc.select("body > p:nth-of-type(2)").text() == "Would you like to create it?"
    }

    def "markdown() should render content correctly"() {
        given:
        def tempFile = Files.createTempFile("test", ".md")
        configService.getDocsDirectory() >> tempFile.toFile().parentFile.absolutePath
        Files.write(tempFile, content.getBytes())

        when:
        def response = controller.markdownViewer(tempFile.fileName.toString(), Map.of())

        then:
        Document doc = Jsoup.parse(response.body.toString())
        response.statusCode == HttpStatus.OK
        doc.select(selector).first().toString().replaceAll("\n *", "") == expectedOutput

        cleanup:
        Files.deleteIfExists(tempFile)

        where:
        content                                                        | selector               | expectedOutput
        "# Test"                                                       | "#markdownViewer > h1" | "<h1>Test</h1>"
        "This is [red]important[/red] and [red]urgent[/red]!"          | "#markdownViewer > p"  | "<p>This is <span class=\"color-red\">important</span> and <span class=\"color-red\">urgent</span>!</p>"
        "This is [red]important[/red]<br>[red]urgent[/red]!"           | "#markdownViewer > p"  | "<p>This is <span class=\"color-red\">important</span><br><span class=\"color-red\">urgent</span>!</p>"
        "This is [red]important[/red]<br>[red]urgent[/red1]!"          | "#markdownViewer > p"  | "<p>This is <span class=\"color-red\">important</span><br>[red]urgent[/red1]!</p>"
        "Checklist: [-v-] Task 1, [-x-] Task 2, [-v-] Task 3."         | "#markdownViewer > p"  | "<p>Checklist: ✓ Task 1, ✗ Task 2, ✓ Task 3.</p>"
        "Checklist: <br>[-v-] Task 1, <br>[-x-] Task 2, [-v-] Task 3." | "#markdownViewer > p"  | "<p>Checklist: <br>✓ Task 1, <br>✗ Task 2, ✓ Task 3.</p>"
        "check [-w-] This is a warning."                               | "#markdownViewer > p"  | "<p>check ⚠ This is a warning.</p>"
        "check [-s-] This is a star."                                  | "#markdownViewer > p"  | "<p>check ★ This is a star.</p>"
        "check [-a-] This is an arrow."                               | "#markdownViewer > p"  | "<p>check ➤ This is an arrow.</p>"

    }

    def "markdownEditor() should render the editor with the file content"() {
        given:
        def tempFile = Files.createTempFile("test", ".md")
        def content = "# Edit mode\nLine 2"
        configService.getDocsDirectory() >> tempFile.toFile().parentFile.absolutePath
        Files.write(tempFile, content.getBytes())
        def fileTime = Files.getLastModifiedTime(tempFile)
        def expectedTimestamp = LocalDateTime.ofInstant(fileTime.toInstant(), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        when:
        def response = controller.markdownEditor(tempFile.fileName.toString())

        then:
        response.statusCode == HttpStatus.OK
        Document doc = Jsoup.parse(response.body.toString())
        doc.select("#easyMdeEditor").text() == content
        doc.select("#md-last-modified-time-editor").text() == expectedTimestamp

        cleanup:
        Files.deleteIfExists(tempFile)
    }

    def "markdownEditor() should return an error when the file is missing"() {
        given:
        def tempDir = Files.createTempDirectory("test")
        configService.getDocsDirectory() >> tempDir.toFile().absolutePath

        when:
        def response = controller.markdownEditor("missing.md")

        then:
        response.statusCode == HttpStatus.INTERNAL_SERVER_ERROR
        response.body.toString().contains("File does not exist missing.md")

        cleanup:
        tempDir.toFile().deleteDir()
    }

    def "markdown() should return 404 for invalid filenames"() {
        given:
        def tempDir = Files.createTempDirectory("test")
        configService.getDocsDirectory() >> tempDir.toFile().absolutePath

        when:
        def response = controller.markdown("invalid\u0000name.md", Map.of(), false)

        then:
        response.statusCode == HttpStatus.NOT_FOUND
        response.body.toString().startsWith("Invalid path")

        cleanup:
        tempDir.toFile().deleteDir()
    }

    def "markdown() should treat slash filename as docs root directory"() {
        given:
        def tempDir = Files.createTempDirectory("test")
        configService.getDocsDirectory() >> tempDir.toFile().absolutePath

        when:
        def response = controller.markdown("/", Map.of(), false)

        then:
        response.statusCode == HttpStatus.FOUND
        response.headers.getFirst("Location") == "/renderDirectoryContents?directoryName=."

        cleanup:
        tempDir.toFile().deleteDir()
    }

    def "markdown() should render markdown content with default template"() {
        given:
        def tempDir = Files.createTempDirectory("test")
        def markdownFile = tempDir.resolve("notes.md")
        configService.getDocsDirectory() >> tempDir.toFile().absolutePath
        Files.write(markdownFile, "# Header".getBytes())

        when:
        def response = controller.markdown("notes.md", Map.of(), false)

        then:
        response.statusCode == HttpStatus.OK
        Document doc = Jsoup.parse(response.body.toString())
        doc.select("#markdownViewer").size() == 1

        cleanup:
        tempDir.toFile().deleteDir()
    }

    def "markdown() should redirect image files to viewer"() {
        given:
        def tempDir = Files.createTempDirectory("test")
        configService.getDocsDirectory() >> tempDir.toFile().absolutePath
        Files.write(tempDir.resolve("picture.png"), [1, 2, 3] as byte[])

        when:
        def response = controller.markdown("picture.png", Map.of(), false)

        then:
        response.statusCode == HttpStatus.FOUND
        response.headers.getFirst("Location") == "/image?filename=picture.png"

        cleanup:
        tempDir.toFile().deleteDir()
    }

    def "markdown() should render yaml files"() {
        given:
        def tempDir = Files.createTempDirectory("test")
        def yamlFile = tempDir.resolve("config.yaml")
        configService.getDocsDirectory() >> tempDir.toFile().absolutePath
        Files.write(yamlFile, "key: value".getBytes())

        when:
        def response = controller.markdown("config.yaml", Map.of(), false)

        then:
        response.statusCode == HttpStatus.OK
        Document doc = Jsoup.parse(response.body.toString())
        doc.select("h1").text() == "config.yaml"
        doc.select("pre > code").text() == "key: value"

        cleanup:
        tempDir.toFile().deleteDir()
    }

    def "markdown() should render groovy files"() {
        given:
        def tempDir = Files.createTempDirectory("test")
        def groovyFile = tempDir.resolve("script.groovy")
        configService.getDocsDirectory() >> tempDir.toFile().absolutePath
        Files.write(groovyFile, "println 'groovy'".getBytes())

        when:
        def response = controller.markdown("script.groovy", Map.of(), false)

        then:
        response.statusCode == HttpStatus.OK
        Document doc = Jsoup.parse(response.body.toString())
        doc.select("h1").text() == "script.groovy"
        doc.select("pre > code").text() == "println 'groovy'"

        cleanup:
        tempDir.toFile().deleteDir()
    }

    def "markdown() should render mermaid files"() {
        given:
        def tempDir = Files.createTempDirectory("test")
        def mermaidFile = tempDir.resolve("diagram.mermaid")
        configService.getDocsDirectory() >> tempDir.toFile().absolutePath
        Files.write(mermaidFile, "graph TD".getBytes())

        when:
        def response = controller.markdown("diagram.mermaid", Map.of(), false)

        then:
        response.statusCode == HttpStatus.OK
        Document doc = Jsoup.parse(response.body.toString())
        doc.select("h1").text() == "diagram.mermaid"
        doc.select("pre > code").text() == "graph TD"

        cleanup:
        tempDir.toFile().deleteDir()
    }

    def "markdown() should render javascript files"() {
        given:
        def tempDir = Files.createTempDirectory("test")
        def jsFile = tempDir.resolve("script.js")
        configService.getDocsDirectory() >> tempDir.toFile().absolutePath
        Files.write(jsFile, "console.log('hi')".getBytes())

        when:
        def response = controller.markdown("script.js", Map.of(), false)

        then:
        response.statusCode == HttpStatus.OK
        Document doc = Jsoup.parse(response.body.toString())
        doc.select("h1").text() == "script.js"
        doc.select("pre > code").text() == "console.log('hi')"

        cleanup:
        tempDir.toFile().deleteDir()
    }

    def "markdown() should render sql files"() {
        given:
        def tempDir = Files.createTempDirectory("test")
        def sqlFile = tempDir.resolve("query.sql")
        configService.getDocsDirectory() >> tempDir.toFile().absolutePath
        Files.write(sqlFile, "select 1".getBytes())

        when:
        def response = controller.markdown("query.sql", Map.of(), false)

        then:
        response.statusCode == HttpStatus.OK
        Document doc = Jsoup.parse(response.body.toString())
        doc.select("h1").text() == "query.sql"
        doc.select("pre > code").text() == "select 1"

        cleanup:
        tempDir.toFile().deleteDir()
    }

    def "markdown() should render plantuml files"() {
        given:
        def tempDir = Files.createTempDirectory("test")
        def plantumlFile = tempDir.resolve("diagram.puml")
        configService.getDocsDirectory() >> tempDir.toFile().absolutePath
        Files.write(plantumlFile, "@startuml".getBytes())

        when:
        def response = controller.markdown("diagram.puml", Map.of(), false)

        then:
        response.statusCode == HttpStatus.OK
        Document doc = Jsoup.parse(response.body.toString())
        doc.select("h1").text() == "diagram.puml"
        doc.select("img").attr("src").contains("plantuml?filename=diagram.puml")

        cleanup:
        tempDir.toFile().deleteDir()
    }

    def "markdown() should render other files as plain text"() {
        given:
        def tempDir = Files.createTempDirectory("test")
        def txtFile = tempDir.resolve("notes.txt")
        configService.getDocsDirectory() >> tempDir.toFile().absolutePath
        Files.write(txtFile, "plain text".getBytes())

        when:
        def response = controller.markdown("notes.txt", Map.of(), false)

        then:
        response.statusCode == HttpStatus.OK
        response.body.toString() == "plain text"

        cleanup:
        tempDir.toFile().deleteDir()
    }

    def "mermaidPlayground() should render the playground template"() {
        when:
        def response = controller.mermaidPlayground()

        then:
        response.statusCode == HttpStatus.OK
        response.headers.getContentType().toString().contains("text/html")
        response.body.toString().contains("<title>Mermaid playground</title>")
        response.body.toString().contains("<div class=\"container\">")
    }

    def "mermaidPlayground() should surface template errors"() {
        given:
        def failingEngine = Mock(TemplateEngine)
        failingEngine.render(_ as String, _ as Map, _ as TemplateOutput) >> {
            throw new IllegalStateException("boom")
        }
        def failingController = new MarkdownController(markdownRenderer, failingEngine, configService)

        when:
        def response = failingController.mermaidPlayground()

        then:
        response.statusCode == HttpStatus.INTERNAL_SERVER_ERROR
        response.body.toString().contains("Error rendering mermaid playground: boom")
    }

    def "markdown() should interpret image location as relative to current document"() {
        given:
        def tempDirectory = Files.createTempDirectory("test").toFile()
        new File(tempDirectory, "nested1/nested2").mkdirs()
        var newFile = new File(tempDirectory, "nested1/nested2/new-file.md")

        Files.write(newFile.toPath(), """# Test\n![image](image.png)""".getBytes())
        configService.getDocsDirectory() >> tempDirectory.absolutePath

        when:
        def response = controller.markdownViewer("nested1/nested2/new-file.md", Map.of())

        then:
        Document doc = Jsoup.parse(response.body.toString())
        response.statusCode == HttpStatus.OK
        doc.select("#markdownViewer > h1").text() == "Test"
        doc.select("#markdownViewer > h1 + p > img").get(0).attr("src").endsWith("filename=nested1/nested2/image.png")

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
        tempDir.toFile().deleteDir()
    }

    def "saveMarkdown() should save markdown content when no conflicts exist"() {
        given:
        def tempFile = Files.createTempFile("test", ".md")
        def content = "# New content"
        configService.getDocsDirectory() >> tempFile.toFile().parentFile.absolutePath
        Files.write(tempFile, content.getBytes())
        Files.setLastModifiedTime(tempFile,
                FileTime.fromMillis(System.currentTimeMillis() - 24 * 60 * 60 * 1000))
        var lastModifiedTime = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        when:
        def response = controller.saveMarkdown(tempFile.fileName.toString(),
                lastModifiedTime, content)

        then:
        response.statusCode == HttpStatus.OK
        !response.body.conflict
        response.body.newFilename == tempFile.fileName.toString()
        Files.readString(tempFile) == content

        cleanup:
        Files.deleteIfExists(tempFile)
    }

    def "saveMarkdown() should create conflict file when file was modified after editor timestamp"() {
        given:
        def tempFile = Files.createTempFile("test", ".md")
        def content = "# New content"
        configService.getDocsDirectory() >> tempFile.toFile().parentFile.absolutePath
        Files.write(tempFile, "# Original content".getBytes())

        // Set editor timestamp to 2 hours ago
        def editorTimestamp = LocalDateTime.now()
                .minusHours(2)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        // Set file timestamp to 1 hour ago (newer than editor)
        Files.setLastModifiedTime(tempFile,
                FileTime.fromMillis(System.currentTimeMillis() - 60 * 60 * 1000))

        when:
        def response = controller.saveMarkdown(tempFile.fileName.toString(),
                editorTimestamp, content)

        then:
        response.statusCode == HttpStatus.CONFLICT
        response.body.conflict
        response.body.newFilename == tempFile.fileName.toString() + "_conflict"
        response.body.timestampOfFileInEditor == editorTimestamp
        Files.exists(tempFile.resolveSibling(response.body.newFilename))
        Files.readString(tempFile.resolveSibling(response.body.newFilename)) == content
        Files.readString(tempFile) == "# Original content"

        cleanup:
        Files.deleteIfExists(tempFile)
        Files.deleteIfExists(tempFile.resolveSibling(response.body.newFilename))
    }

    def "Markdown with a sql error should render the error message"() {
        given:
        def tempFile = Files.createTempFile("test", ".md")
        configService.getDocsDirectory() >> tempFile.toFile().parentFile.absolutePath
        Files.write(tempFile, "# Test\n```sql(missing-database)\nselect * from user\n```".getBytes())

        when:
        def response = controller.markdownViewer(tempFile.fileName.toString(), Map.of())

        then:
        Document doc = Jsoup.parse(response.body.toString())
        response.statusCode == HttpStatus.OK
        doc.select("#markdownViewer > h1").text() == "Test"
        doc.select("#markdownViewer > h1 + pre > code").text() == "select * from user"
        doc.select("#markdownViewer > h1 + pre > code").get(0).attr("class") == "language-hidden-sql"
        // TODO: check for error message

        cleanup:
        Files.deleteIfExists(tempFile)
    }
}