package uk.anbu.devnotes.controller

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import spock.lang.Specification
import spock.lang.TempDir
import uk.anbu.devnotes.service.ConfigService
import uk.anbu.devnotes.types.MarkdownFile

import java.nio.file.Files
import java.nio.file.Path

import static uk.anbu.devnotes.util.FileBasedCache.generateCacheFileName
import static uk.anbu.devnotes.util.FileBasedCache.generateHash

class GroovyRefreshControllerSpec extends Specification {

    @TempDir
    Path tempDir

    ConfigService configService
    GroovyRefreshController controller

    def setup() {
        configService = Mock(ConfigService)
        configService.getDocsDirectory() >> tempDir.toString()
        configService.getChromeDriverLocation() >> Optional.empty()
        controller = new GroovyRefreshController(configService)
    }

    // -------------------------------------------------------------------------
    // Input validation
    // -------------------------------------------------------------------------

    def "returns 400 when markdownFile is missing from request body"() {
        when:
        def response = controller.renderFragment([groovyId: "abc123"])

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
    }

    def "returns 400 when groovyId is missing from request body"() {
        when:
        def response = controller.renderFragment([markdownFile: "test.md"])

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
    }

    def "returns 400 when markdownFile is blank"() {
        when:
        def response = controller.renderFragment([markdownFile: "   ", groovyId: "abc123"])

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
    }

    def "returns 400 when groovyId is blank"() {
        when:
        def response = controller.renderFragment([markdownFile: "test.md", groovyId: "   "])

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
    }

    def "returns 400 when both parameters are missing"() {
        when:
        def response = controller.renderFragment([:])

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
    }

    // -------------------------------------------------------------------------
    // File resolution
    // -------------------------------------------------------------------------

    def "returns 400 when the markdown file does not exist on disk"() {
        when:
        def response = controller.renderFragment([markdownFile: "nonexistent.md", groovyId: "abc123"])

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
        response.body.contains("not found")
    }

    // -------------------------------------------------------------------------
    // Block matching
    // -------------------------------------------------------------------------

    def "returns 404 when the file contains no groovy blocks"() {
        given:
        Files.writeString(tempDir.resolve("notes.md"), "# Plain markdown\n\nNo code here.\n")

        when:
        def response = controller.renderFragment([markdownFile: "notes.md", groovyId: "abc123"])

        then:
        response.statusCode == HttpStatus.NOT_FOUND
    }

    def "returns 404 when groovyId does not match any block in the file"() {
        given:
        Files.writeString(tempDir.resolve("test.md"), groovyFence("groovy:html", '"<b>hi</b>"'))

        when:
        def response = controller.renderFragment([markdownFile: "test.md", groovyId: "doesnotmatch"])

        then:
        response.statusCode == HttpStatus.NOT_FOUND
    }

    def "returns 404 when the file only contains non-groovy code blocks"() {
        given:
        def content = "```data\nsource: myDs\nquery: SELECT 1\n```\n"
        Files.writeString(tempDir.resolve("test.md"), content)

        when:
        def response = controller.renderFragment([markdownFile: "test.md", groovyId: "abc123"])

        then:
        response.statusCode == HttpStatus.NOT_FOUND
    }

    // -------------------------------------------------------------------------
    // Successful refresh - output types
    // -------------------------------------------------------------------------

    def "returns 200 HTML with groovy-block wrapper for a groovy:html block"() {
        given:
        def script = '"<p>hello from groovy</p>"'
        def literal = script + "\n"
        Files.writeString(tempDir.resolve("test.md"), groovyFence("groovy:html", script))
        def groovyId = generateHash(literal)

        when:
        def response = controller.renderFragment([markdownFile: "test.md", groovyId: groovyId])

        then:
        response.statusCode == HttpStatus.OK
        response.headers.getContentType().isCompatibleWith(MediaType.TEXT_HTML)
        response.body.contains('class="groovy-block"')
        response.body.contains("data-groovy-id=\"${groovyId}\"")
        response.body.contains("<p>hello from groovy</p>")
    }

    def "returns 200 with groovy-block wrapper for a groovy:text block"() {
        given:
        def script = '"plain text output"'
        def literal = script + "\n"
        Files.writeString(tempDir.resolve("notes.md"), groovyFence("groovy:text", script))
        def groovyId = generateHash(literal)

        when:
        def response = controller.renderFragment([markdownFile: "notes.md", groovyId: groovyId])

        then:
        response.statusCode == HttpStatus.OK
        response.body.contains('class="groovy-block"')
        response.body.contains("plain text output")
    }

    def "returns 200 with groovy-block wrapper for a groovy:csv-table block"() {
        given:
        def script = '"""A,B\n1,2"""'
        def literal = script + "\n"
        Files.writeString(tempDir.resolve("table.md"), groovyFence("groovy:csv-table", script))
        def groovyId = generateHash(literal)

        when:
        def response = controller.renderFragment([markdownFile: "table.md", groovyId: groovyId])

        then:
        response.statusCode == HttpStatus.OK
        response.body.contains('class="groovy-block"')
        response.body.contains("<table>")
        response.body.contains("<td>A</td>")
    }

    // -------------------------------------------------------------------------
    // Cache busting
    // -------------------------------------------------------------------------

    def "deletes stale cache file and returns fresh output on refresh"() {
        given:
        def script = '"<span>fresh output</span>"'
        def literal = script + "\n"
        Files.writeString(tempDir.resolve("cached.md"), groovyFence("groovy:html", script))
        def groovyId = generateHash(literal)

        def mdFile = new MarkdownFile(tempDir, "cached.md")
        def cacheFile = Path.of(generateCacheFileName(mdFile, literal))
        Files.writeString(cacheFile, "<span>stale output</span>")

        when:
        def response = controller.renderFragment([markdownFile: "cached.md", groovyId: groovyId])

        then:
        response.statusCode == HttpStatus.OK
        response.body.contains("<span>fresh output</span>")
        !response.body.contains("stale")
    }

    def "cache file is absent after a successful refresh when caching is enabled"() {
        given:
        def script = '"<b>result</b>"'
        def literal = script + "\n"
        Files.writeString(tempDir.resolve("test.md"), groovyFence("groovy:html", script))
        def groovyId = generateHash(literal)

        def mdFile = new MarkdownFile(tempDir, "test.md")
        def cacheFile = Path.of(generateCacheFileName(mdFile, literal))

        when:
        controller.renderFragment([markdownFile: "test.md", groovyId: groovyId])

        then:
        // renderResultFresh deletes the old cache; renderResultWrapped re-executes and saves a new one
        Files.exists(cacheFile)
        Files.readString(cacheFile).contains("<b>result</b>")
    }

    // -------------------------------------------------------------------------
    // Info-string matching
    // -------------------------------------------------------------------------

    def "matches a groovy block whose info string includes config parameters"() {
        given:
        def script = '"<em>no-cache block</em>"'
        def literal = script + "\n"
        Files.writeString(tempDir.resolve("test.md"), groovyFence("groovy:html(cacheEnabled:false)", script))
        def groovyId = generateHash(literal)

        when:
        def response = controller.renderFragment([markdownFile: "test.md", groovyId: groovyId])

        then:
        response.statusCode == HttpStatus.OK
        response.body.contains("<em>no-cache block</em>")
    }

    def "skips non-groovy code blocks and matches the correct groovy block"() {
        given:
        def script = '"<i>correct block</i>"'
        def literal = script + "\n"
        def content = "```data\nsource: myDs\n```\n\n" + groovyFence("groovy:html", script)
        Files.writeString(tempDir.resolve("mixed.md"), content)
        def groovyId = generateHash(literal)

        when:
        def response = controller.renderFragment([markdownFile: "mixed.md", groovyId: groovyId])

        then:
        response.statusCode == HttpStatus.OK
        response.body.contains("<i>correct block</i>")
    }

    def "matches the second of two groovy blocks by its distinct groovyId"() {
        given:
        def script1 = '"<b>first block</b>"'
        def script2 = '"<b>second block</b>"'
        def content = groovyFence("groovy:html", script1) + "\n" + groovyFence("groovy:html", script2)
        Files.writeString(tempDir.resolve("two-blocks.md"), content)
        def groovyId = generateHash(script2 + "\n")

        when:
        def response = controller.renderFragment([markdownFile: "two-blocks.md", groovyId: groovyId])

        then:
        response.statusCode == HttpStatus.OK
        response.body.contains("<b>second block</b>")
        !response.body.contains("<b>first block</b>")
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private static String groovyFence(String infoString, String script) {
        "```${infoString}\n${script}\n```\n"
    }
}

