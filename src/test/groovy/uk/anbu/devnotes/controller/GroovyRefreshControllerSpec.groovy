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
        Files.writeString(tempDir.resolve("test.md"), groovyFenceFromLiteral(groovyLiteral("html", '"<b>hi</b>"')))

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

    def "returns 200 HTML with groovy-block wrapper for an html block"() {
        given:
        def literal = groovyLiteral("html", '"<p>hello from groovy</p>"')
        Files.writeString(tempDir.resolve("test.md"), groovyFenceFromLiteral(literal))
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

    def "returns 200 with groovy-block wrapper for a text block"() {
        given:
        def literal = groovyLiteral("text", '"plain text output"')
        Files.writeString(tempDir.resolve("notes.md"), groovyFenceFromLiteral(literal))
        def groovyId = generateHash(literal)

        when:
        def response = controller.renderFragment([markdownFile: "notes.md", groovyId: groovyId])

        then:
        response.statusCode == HttpStatus.OK
        response.body.contains('class="groovy-block"')
        response.body.contains("plain text output")
    }

    def "returns 200 with groovy-block wrapper for a csv-table block"() {
        given:
        def literal = groovyLiteral("csv-table", '"""A,B\n1,2"""')
        Files.writeString(tempDir.resolve("table.md"), groovyFenceFromLiteral(literal))
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
        def literal = groovyLiteral("html", '"<span>fresh output</span>"')
        Files.writeString(tempDir.resolve("cached.md"), groovyFenceFromLiteral(literal))
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

    def "cache file is written after a successful refresh when caching is enabled"() {
        given:
        def literal = groovyLiteral("html", '"<b>result</b>"')
        Files.writeString(tempDir.resolve("test.md"), groovyFenceFromLiteral(literal))
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

    def "cache-enabled false does not leave a cache file after refresh"() {
        given:
        def literal = groovyLiteral("html", '"<em>no-cache</em>"', "cache-enabled: false\n")
        Files.writeString(tempDir.resolve("no-cache.md"), groovyFenceFromLiteral(literal))
        def groovyId = generateHash(literal)
        def mdFile = new MarkdownFile(tempDir, "no-cache.md")
        def cacheFile = Path.of(generateCacheFileName(mdFile, literal))

        when:
        def response = controller.renderFragment([markdownFile: "no-cache.md", groovyId: groovyId])

        then:
        response.statusCode == HttpStatus.OK
        response.body.contains("<em>no-cache</em>")
        !Files.exists(cacheFile)
    }

    // -------------------------------------------------------------------------
    // Block selection
    // -------------------------------------------------------------------------

    def "skips non-groovy code blocks and matches the correct groovy block"() {
        given:
        def literal = groovyLiteral("html", '"<i>correct block</i>"')
        def content = "```data\nsource: myDs\n```\n\n" + groovyFenceFromLiteral(literal)
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
        def literal1 = groovyLiteral("html", '"<b>first block</b>"')
        def literal2 = groovyLiteral("html", '"<b>second block</b>"')
        def content = groovyFenceFromLiteral(literal1) + "\n" + groovyFenceFromLiteral(literal2)
        Files.writeString(tempDir.resolve("two-blocks.md"), content)
        def groovyId = generateHash(literal2)

        when:
        def response = controller.renderFragment([markdownFile: "two-blocks.md", groovyId: groovyId])

        then:
        response.statusCode == HttpStatus.OK
        response.body.contains("<b>second block</b>")
        !response.body.contains("<b>first block</b>")
    }

    def "controls-enabled false suppresses context-menu attribute"() {
        given:
        def literal = groovyLiteral("text", '"no menu"', "controls-enabled: false\n")
        Files.writeString(tempDir.resolve("no-ctrl.md"), groovyFenceFromLiteral(literal))
        def groovyId = generateHash(literal)

        when:
        def response = controller.renderFragment([markdownFile: "no-ctrl.md", groovyId: groovyId])

        then:
        response.statusCode == HttpStatus.OK
        response.body.contains('data-groovy-controls="false"')
    }

    // =========================================================================
    // POST /groovy/edit
    // =========================================================================

    def "edit - returns 400 when markdownFile is missing"() {
        when:
        def response = controller.editBlock([blockId: "abc", newContent: groovyLiteral("html", '"hi"')])

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
    }

    def "edit - returns 400 when blockId is missing"() {
        when:
        def response = controller.editBlock([markdownFile: "test.md", newContent: groovyLiteral("html", '"hi"')])

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
    }

    def "edit - returns 400 when newContent is missing"() {
        when:
        def response = controller.editBlock([markdownFile: "test.md", blockId: "abc"])

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
    }

    def "edit - returns 400 when the markdown file does not exist"() {
        when:
        def response = controller.editBlock([
                markdownFile: "nonexistent.md",
                blockId     : "abc",
                newContent  : groovyLiteral("html", '"hi"')
        ])

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
        response.body.contains("not found")
    }

    def "edit - returns 409 when file was modified after the client timestamp"() {
        given:
        Files.writeString(tempDir.resolve("test.md"), "# hello\n")

        when:
        def response = controller.editBlock([
                markdownFile    : "test.md",
                blockId         : "abc",
                newContent      : groovyLiteral("html", '"hi"'),
                fileLastModified: "2020-01-01 00:00:00"
        ])

        then:
        response.statusCode == HttpStatus.CONFLICT
    }

    def "edit - returns 404 when blockId does not match any groovy block"() {
        given:
        def literal = groovyLiteral("html", '"<b>existing</b>"')
        Files.writeString(tempDir.resolve("test.md"), groovyFenceFromLiteral(literal))

        when:
        def response = controller.editBlock([
                markdownFile: "test.md",
                blockId     : "doesnotmatch",
                newContent  : groovyLiteral("html", '"<b>new</b>"')
        ])

        then:
        response.statusCode == HttpStatus.NOT_FOUND
    }

    def "edit - updates the markdown file on disk with the new content"() {
        given:
        def literal = groovyLiteral("html", '"<b>old</b>"')
        def mdFile = tempDir.resolve("update.md")
        Files.writeString(mdFile, groovyFenceFromLiteral(literal))
        def blockId = generateHash(literal)
        def newLiteral = groovyLiteral("html", '"<b>updated</b>"')

        when:
        def response = controller.editBlock([
                markdownFile: "update.md",
                blockId     : blockId,
                newContent  : newLiteral
        ])

        then:
        response.statusCode == HttpStatus.OK
        Files.readString(mdFile).contains('"<b>updated</b>"')
        !Files.readString(mdFile).contains('"<b>old</b>"')
    }

    def "edit - deletes old cache file and returns HTML reflecting new content"() {
        given:
        def literal = groovyLiteral("html", '"<span>old output</span>"')
        def mdFile = tempDir.resolve("cached.md")
        Files.writeString(mdFile, groovyFenceFromLiteral(literal))
        def blockId = generateHash(literal)

        def mdFileObj = new MarkdownFile(tempDir, "cached.md")
        def oldCacheFile = Path.of(generateCacheFileName(mdFileObj, literal))
        Files.writeString(oldCacheFile, "<span>stale cached content</span>")

        def newLiteral = groovyLiteral("html", '"<span>fresh content</span>"')

        when:
        def response = controller.editBlock([
                markdownFile: "cached.md",
                blockId     : blockId,
                newContent  : newLiteral
        ])

        then:
        response.statusCode == HttpStatus.OK
        !Files.exists(oldCacheFile)
        response.body.contains("<span>fresh content</span>")
    }

    def "edit - returned HTML carries the new block ID derived from the new content"() {
        given:
        def literal = groovyLiteral("html", '"<em>original</em>"')
        Files.writeString(tempDir.resolve("ids.md"), groovyFenceFromLiteral(literal))
        def blockId = generateHash(literal)

        def newLiteral = groovyLiteral("html", '"<em>changed</em>"')
        // server ensures trailing newline before hashing
        def safeNewLiteral = newLiteral.endsWith("\n") ? newLiteral : newLiteral + "\n"
        def expectedNewId = generateHash(safeNewLiteral)

        when:
        def response = controller.editBlock([
                markdownFile: "ids.md",
                blockId     : blockId,
                newContent  : newLiteral
        ])

        then:
        response.statusCode == HttpStatus.OK
        response.body.contains("data-groovy-id=\"${expectedNewId}\"")
    }

    def "edit - returns X-File-Last-Modified header with correct timestamp format"() {
        given:
        def literal = groovyLiteral("html", '"<p>hello</p>"')
        Files.writeString(tempDir.resolve("hdr.md"), groovyFenceFromLiteral(literal))
        def blockId = generateHash(literal)

        when:
        def response = controller.editBlock([
                markdownFile: "hdr.md",
                blockId     : blockId,
                newContent  : groovyLiteral("html", '"<p>world</p>"')
        ])

        then:
        response.statusCode == HttpStatus.OK
        response.headers.getFirst("X-File-Last-Modified") =~ /\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}/
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Builds a fence literal: {@code "output: <type>\n[extraYaml]---\n<script>\n"} */
    private static String groovyLiteral(String outputType, String script, String extraYaml = "") {
        "output: ${outputType}\n${extraYaml}---\n${script}\n"
    }

    /** Wraps a literal body inside a {@code ```groovy-exec} fence. */
    private static String groovyFenceFromLiteral(String literal) {
        "```groovy-exec\n${literal}```\n"
    }
}

