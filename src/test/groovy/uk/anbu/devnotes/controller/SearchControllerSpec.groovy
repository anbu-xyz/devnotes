package uk.anbu.devnotes.controller

import com.fasterxml.jackson.databind.ObjectMapper
import gg.jte.ContentType
import gg.jte.TemplateEngine
import gg.jte.resolve.DirectoryCodeResolver
import org.jsoup.Jsoup
import org.springframework.http.HttpStatus
import spock.lang.Specification
import spock.lang.TempDir
import uk.anbu.devnotes.service.ConfigService

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class SearchControllerSpec extends Specification {

    @TempDir
    Path tempDir

    SearchController controller

    def setup() {
        ConfigService configService = Mock(ConfigService)
        configService.getDocsDirectory() >> tempDir.toString()

        def codeResolver = new DirectoryCodeResolver(Paths.get("src/main/jte"))
        def te = TemplateEngine.create(codeResolver, Paths.get("src/main/jte"), ContentType.Html)

        controller = new SearchController(configService, te, new ObjectMapper())
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void writeMarkdown(String relativePath, String content) {
        def file = tempDir.resolve(relativePath)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
    }

    private static Map parseJson(String body) {
        new ObjectMapper().readValue(body, Map)
    }

    // =========================================================================
    // Plain-text search — HTML output
    // =========================================================================

    def "plain text search with multiple results returns 200 HTML with result count"() {
        given:
        writeMarkdown("file1.md", "Hello world from file one")
        writeMarkdown("file2.md", "Hello world from file two")

        when:
        def response = controller.search("hello", "", false, false, false)

        then:
        response.statusCode == HttpStatus.OK
        def doc = Jsoup.parse(response.body)
        doc.select(".search-count").text().contains("2 results found")
        doc.select(".search-meta-query").text().contains("hello")
    }

    def "plain text search with no results returns 200 HTML with no-results message"() {
        given:
        writeMarkdown("file1.md", "Some content here")

        when:
        def response = controller.search("nonexistent", "", false, false, false)

        then:
        response.statusCode == HttpStatus.OK
        def doc = Jsoup.parse(response.body)
        doc.body().text().contains("No results found")
    }

    def "plain text search with single result redirects to the file"() {
        given:
        writeMarkdown("notes/the-file.md", "Unique phrase only in this file")

        when:
        def response = controller.search("unique phrase only", "", false, false, false)

        then:
        response.statusCode == HttpStatus.FOUND
        response.headers.getFirst("Location") == "/markdown?filename=notes/the-file.md"
    }

    def "HTML result page lists file links for multiple matches"() {
        given:
        writeMarkdown("alpha.md", "common term alpha")
        writeMarkdown("beta.md", "common term beta")

        when:
        def response = controller.search("common term", "", false, false, false)

        then:
        response.statusCode == HttpStatus.OK
        def doc = Jsoup.parse(response.body)
        def links = doc.select("a.search-result-link")*.text()*.trim()
        links.sort() == ["alpha.md", "beta.md"]
    }

    // =========================================================================
    // Plain-text search — JSON output
    // =========================================================================

    def "plain text search with results returns JSON when json=true"() {
        given:
        writeMarkdown("file1.md", "Alpha content here")
        writeMarkdown("file2.md", "Beta content here")

        when:
        def response = controller.search("content", "", false, false, true)

        then:
        response.statusCode == HttpStatus.OK
        response.headers.getContentType().toString().contains("application/json")
        def json = parseJson(response.body)
        (json.results as List).size() == 2
    }

    def "plain text search with no results returns 404 when json=true"() {
        given:
        writeMarkdown("file1.md", "Some content")

        when:
        def response = controller.search("nonexistent", "", false, false, true)

        then:
        response.statusCode == HttpStatus.NOT_FOUND
    }

    def "JSON result contains fileName and preview for each match"() {
        given:
        writeMarkdown("docs/guide.md", "This is a guide about testing")

        when:
        def response = controller.search("testing", "", false, false, true)

        then:
        response.statusCode == HttpStatus.OK
        def json = parseJson(response.body)
        def result = (json.results as List)[0] as Map
        result.fileName == "docs/guide.md"
        (result.preview as String).contains("testing")
    }

    // =========================================================================
    // Case sensitivity
    // =========================================================================

    def "case-sensitive search finds only the exact-case match"() {
        given:
        writeMarkdown("upper.md", "Contains UPPERCASE word")
        writeMarkdown("lower.md", "Contains lowercase word")

        when:
        def response = controller.search("UPPERCASE", "", true, false, true)

        then:
        response.statusCode == HttpStatus.OK
        def json = parseJson(response.body)
        (json.results as List).size() == 1
        (json.results as List)[0].fileName == "upper.md"
    }

    def "case-insensitive search finds both case variants"() {
        given:
        writeMarkdown("upper.md", "Contains UPPERCASE word")
        writeMarkdown("lower.md", "Contains uppercase word")

        when:
        def response = controller.search("uppercase", "", false, false, true)

        then:
        response.statusCode == HttpStatus.OK
        def json = parseJson(response.body)
        (json.results as List).size() == 2
    }

    // =========================================================================
    // Path (subdirectory) filtering
    // =========================================================================

    def "search with path parameter restricts results to the subdirectory"() {
        given:
        writeMarkdown("subdir/inside.md", "target phrase is here")
        writeMarkdown("outside.md", "target phrase is here too")

        when:
        def response = controller.search("target phrase", "subdir", false, false, true)

        then:
        response.statusCode == HttpStatus.OK
        def json = parseJson(response.body)
        (json.results as List).size() == 1
        ((json.results as List)[0].fileName as String).contains("subdir")
    }

    // =========================================================================
    // Query trimming
    // =========================================================================

    def "leading and trailing whitespace in the query is ignored"() {
        given:
        writeMarkdown("file.md", "Hello world")

        when:
        def response = controller.search("  Hello  ", "", false, false, true)

        then:
        response.statusCode == HttpStatus.OK
        def json = parseJson(response.body)
        (json.results as List).size() == 1
    }

    // =========================================================================
    // Regex search
    // =========================================================================

    def "regex search matches files whose content satisfies the pattern"() {
        given:
        writeMarkdown("file1.md", "Error code 404 occurred")
        writeMarkdown("file2.md", "No errors here")

        when:
        def response = controller.search("Error code \\d+", "", false, true, true)

        then:
        response.statusCode == HttpStatus.OK
        def json = parseJson(response.body)
        (json.results as List).size() == 1
        (json.results as List)[0].fileName == "file1.md"
    }

    def "regex search respects case-insensitive flag"() {
        given:
        writeMarkdown("file1.md", "ERROR code 500")
        writeMarkdown("file2.md", "error code 200")
        writeMarkdown("file3.md", "No match here")

        when:
        def response = controller.search("error code \\d+", "", false, true, true)

        then:
        response.statusCode == HttpStatus.OK
        def json = parseJson(response.body)
        (json.results as List).size() == 2
    }

    def "regex search respects case-sensitive flag"() {
        given:
        writeMarkdown("file1.md", "ERROR code 500")
        writeMarkdown("file2.md", "error code 200")

        when:
        def response = controller.search("error code \\d+", "", true, true, true)

        then:
        response.statusCode == HttpStatus.OK
        def json = parseJson(response.body)
        (json.results as List).size() == 1
        (json.results as List)[0].fileName == "file2.md"
    }

    def "regex search result contains preview around the match"() {
        given:
        writeMarkdown("notes.md", "prefix text " + "x" * 80 + " MATCH-ME " + "y" * 80 + " suffix text")

        when:
        def response = controller.search("MATCH-ME", "", true, true, true)

        then:
        response.statusCode == HttpStatus.OK
        def json = parseJson(response.body)
        def preview = (json.results as List)[0].preview as String
        preview.contains("MATCH-ME")
        preview.endsWith("...\n")
    }

    def "regex search with no matches returns 404 when json=true"() {
        given:
        writeMarkdown("file.md", "Some normal content")

        when:
        def response = controller.search("\\d{10}", "", false, true, true)

        then:
        response.statusCode == HttpStatus.NOT_FOUND
    }

    def "regex search with single match redirects to the file in HTML mode"() {
        given:
        writeMarkdown("only-one.md", "unique regex 42 target")

        when:
        def response = controller.search("regex \\d+", "", false, true, false)

        then:
        response.statusCode == HttpStatus.FOUND
        response.headers.getFirst("Location") == "/markdown?filename=only-one.md"
    }

    // =========================================================================
    // Error handling
    // =========================================================================

    def "invalid regex pattern returns 500 with the bad query in the message"() {
        given:
        writeMarkdown("file.md", "Some content")

        when:
        def response = controller.search("[invalid(regex", "", false, true, false)

        then:
        response.statusCode == HttpStatus.INTERNAL_SERVER_ERROR
        response.body.contains("[invalid(regex")
    }
}