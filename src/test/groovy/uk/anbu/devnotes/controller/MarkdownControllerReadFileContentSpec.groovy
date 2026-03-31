package uk.anbu.devnotes.controller

import gg.jte.TemplateEngine
import gg.jte.TemplateOutput
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import spock.lang.Specification
import spock.lang.TempDir
import uk.anbu.devnotes.module.MarkdownRenderer
import uk.anbu.devnotes.service.ConfigService

import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class MarkdownControllerReadFileContentSpec extends Specification {

    @TempDir
    Path docsDir

    private static final Method READ_FILE_CONTENT

    static {
        READ_FILE_CONTENT = MarkdownController.class
                .getDeclaredMethod("readFileContent", String.class, String.class, Path.class, boolean.class, Map.class)
        READ_FILE_CONTENT.setAccessible(true)
    }

    def "readFileContent renders markdown via the markdown template"() {
        given:
        def templateEngine = Mock(TemplateEngine)
        def controller = new MarkdownController(Mock(MarkdownRenderer), templateEngine, Stub(ConfigService))
        writeFile("page.md", "# Hello")

        when:
        def response = readFileContent(controller, "page.md", "md")

        then:
        response.statusCode == HttpStatus.OK
        response.headers.getFirst(HttpHeaders.CONTENT_TYPE).startsWith("text/html")
        1 * templateEngine.render("render/markdown.jte", { Map params -> params.markdownFile == "page.md" }, _ as TemplateOutput)
    }

    def "readFileContent redirects image requests"() {
        given:
        def templateEngine = Mock(TemplateEngine)
        def controller = new MarkdownController(Mock(MarkdownRenderer), templateEngine, Stub(ConfigService))

        when:
        def response = readFileContent(controller, "diagram.png", "png")

        then:
        response.statusCode == HttpStatus.FOUND
        response.headers.getFirst(HttpHeaders.LOCATION) == "/image?filename=diagram.png"
        0 * templateEngine.render(_, _, _)
    }

    def "readFileContent renders yaml through the yaml template"() {
        given:
        def templateEngine = Mock(TemplateEngine)
        def controller = new MarkdownController(Mock(MarkdownRenderer), templateEngine, Stub(ConfigService))
        writeFile("config.yaml", "value: 1")

        when:
        def response = readFileContent(controller, "config.yaml", "yaml")

        then:
        response.statusCode == HttpStatus.OK
        response.headers.getFirst(HttpHeaders.CONTENT_TYPE).startsWith("text/html")
        1 * templateEngine.render("render/yaml.jte", _, _)
    }

    def "readFileContent renders groovy scripts"() {
        given:
        def templateEngine = Mock(TemplateEngine)
        def controller = new MarkdownController(Mock(MarkdownRenderer), templateEngine, Stub(ConfigService))
        writeFile("script.groovy", "println 'ok'")

        when:
        def response = readFileContent(controller, "script.groovy", "groovy")

        then:
        response.statusCode == HttpStatus.OK
        response.headers.getFirst(HttpHeaders.CONTENT_TYPE).startsWith("text/html")
        1 * templateEngine.render("render/groovy.jte", _, _)
    }

    def "readFileContent renders mermaid diagrams"() {
        given:
        def templateEngine = Mock(TemplateEngine)
        def controller = new MarkdownController(Mock(MarkdownRenderer), templateEngine, Stub(ConfigService))
        writeFile("diagram.mermaid", "graph TD")

        when:
        def response = readFileContent(controller, "diagram.mermaid", "mermaid")

        then:
        response.statusCode == HttpStatus.OK
        response.headers.getFirst(HttpHeaders.CONTENT_TYPE).startsWith("text/html")
        1 * templateEngine.render("render/mermaid.jte", _, _)
    }

    def "readFileContent renders javascript files"() {
        given:
        def templateEngine = Mock(TemplateEngine)
        def controller = new MarkdownController(Mock(MarkdownRenderer), templateEngine, Stub(ConfigService))
        writeFile("module.js", "console.log('ok')")

        when:
        def response = readFileContent(controller, "module.js", "js")

        then:
        response.statusCode == HttpStatus.OK
        response.headers.getFirst(HttpHeaders.CONTENT_TYPE).startsWith("text/html")
        1 * templateEngine.render("render/javascript.jte", _, _)
    }

    def "readFileContent renders sql files"() {
        given:
        def templateEngine = Mock(TemplateEngine)
        def controller = new MarkdownController(Mock(MarkdownRenderer), templateEngine, Stub(ConfigService))
        writeFile("query.sql", "select 1")

        when:
        def response = readFileContent(controller, "query.sql", "sql")

        then:
        response.statusCode == HttpStatus.OK
        response.headers.getFirst(HttpHeaders.CONTENT_TYPE).startsWith("text/html")
        1 * templateEngine.render("render/sql.jte", _, _)
    }

    def "readFileContent renders plantuml files"() {
        given:
        def templateEngine = Mock(TemplateEngine)
        def controller = new MarkdownController(Mock(MarkdownRenderer), templateEngine, Stub(ConfigService))

        when:
        def response = readFileContent(controller, "diagram.puml", "puml")

        then:
        response.statusCode == HttpStatus.OK
        response.headers.getFirst(HttpHeaders.CONTENT_TYPE).startsWith("text/html")
        1 * templateEngine.render("render/plantuml.jte", _, _)
    }

    def "readFileContent serves plain text for unknown extensions"() {
        given:
        def templateEngine = Mock(TemplateEngine)
        def controller = new MarkdownController(Mock(MarkdownRenderer), templateEngine, Stub(ConfigService))
        writeFile("notes.txt", "plain text")

        when:
        def response = readFileContent(controller, "notes.txt", "txt")

        then:
        response.statusCode == HttpStatus.OK
        response.headers.getFirst(HttpHeaders.CONTENT_TYPE).startsWith("text/plain")
        response.body == "plain text"
        0 * templateEngine.render(_, _, _)
    }

    private ResponseEntity<?> readFileContent(MarkdownController controller, String filename, String fileExtension) {
        (ResponseEntity<?>) READ_FILE_CONTENT.invoke(controller, filename, fileExtension, docsDir, false, new HashMap<>())
    }

    private Path writeFile(String relativePath, String content) {
        Path path = docsDir.resolve(relativePath)
        if (path.parent != null) {
            Files.createDirectories(path.parent)
        }
        Files.write(path, content.getBytes(StandardCharsets.UTF_8))
        return path
    }
}

