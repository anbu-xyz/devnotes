package uk.anbu.devnotes.markdown.code

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.commonmark.node.HtmlBlock
import org.jsoup.Jsoup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir
import uk.anbu.devnotes.types.MarkdownFile

import java.nio.file.Files
import java.nio.file.Path

import static com.github.tomakehurst.wiremock.client.WireMock.*

class RestBlockTranslatorSpec extends Specification {

    @Shared
    WireMockServer wireMock

    @TempDir
    Path tempDir

    def setupSpec() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())
        wireMock.start()
    }

    def cleanupSpec() {
        wireMock.stop()
    }

    def setup() {
        wireMock.resetAll()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private int port() { wireMock.port() }

    private MarkdownFile mdFile(String name = "test.md") {
        def file = tempDir.resolve(name)
        Files.writeString(file, "# test")
        new MarkdownFile(tempDir, name)
    }

    private static RestBlockTranslator translator() { new RestBlockTranslator() }

    // -------------------------------------------------------------------------
    // 1. Basic GET rendering
    // -------------------------------------------------------------------------

    def "GET request with JSON array response renders all rows in an HTML table"() {
        given:
        wireMock.stubFor(get(urlEqualTo("/todos"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody('[{"id":1,"title":"Buy milk"},{"id":2,"title":"Walk dog"}]')))

        def yaml = """\
url: http://localhost:${port()}/todos
method: GET
"""
        when:
        def result = translator().translate(yaml, mdFile())

        then:
        result.isPresent()
        result.get() instanceof HtmlBlock
        def doc = Jsoup.parse((result.get() as HtmlBlock).literal)
        doc.select("table.rest-block-table").size() == 1
        doc.select("tbody tr").size() == 2
        doc.select("th").collect { it.text() }.containsAll(["id", "title"])
        doc.select("tbody tr").first().text().contains("Buy milk")
    }

    // -------------------------------------------------------------------------
    // 2. JSONPath extraction
    // -------------------------------------------------------------------------

    def "GET request with JSONPath expression extracts matching elements"() {
        given:
        wireMock.stubFor(get(urlEqualTo("/data"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody('{"items":[{"id":1,"name":"Alpha"},{"id":2,"name":"Beta"}]}')))

        def yaml = """\
url: http://localhost:${port()}/data
jsonpath: "\$.items[*]"
"""
        when:
        def result = translator().translate(yaml, mdFile())

        then:
        result.isPresent()
        def doc = Jsoup.parse((result.get() as HtmlBlock).literal)
        doc.select("tbody tr").size() == 2
        doc.text().contains("Alpha")
        doc.text().contains("Beta")
    }

    // -------------------------------------------------------------------------
    // 3. options.columns
    // -------------------------------------------------------------------------

    def "options.columns filters and orders the rendered columns"() {
        given:
        wireMock.stubFor(get(urlEqualTo("/items"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody('[{"id":1,"name":"Alpha","secret":"hidden"}]')))

        def yaml = """\
url: http://localhost:${port()}/items
options:
  columns: [name, id]
"""
        when:
        def result = translator().translate(yaml, mdFile())

        then:
        result.isPresent()
        def doc = Jsoup.parse((result.get() as HtmlBlock).literal)
        def headers = doc.select("th").collect { it.text() }
        headers == ["name", "id"]
        !headers.contains("secret")
    }

    // -------------------------------------------------------------------------
    // 4. row-limit
    // -------------------------------------------------------------------------

    def "options.row-limit caps the number of rendered rows"() {
        given:
        def rows = (1..10).collect { """{"id":${it}}""" }.join(",")
        wireMock.stubFor(get(urlEqualTo("/many"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[${rows}]")))

        def yaml = """\
url: http://localhost:${port()}/many
options:
  row-limit: 3
"""
        when:
        def result = translator().translate(yaml, mdFile())

        then:
        result.isPresent()
        def doc = Jsoup.parse((result.get() as HtmlBlock).literal)
        doc.select("tbody tr").size() == 3
        doc.select("tfoot").text().contains("limit reached")
    }

    // -------------------------------------------------------------------------
    // 5. POST with body
    // -------------------------------------------------------------------------

    def "POST request with JSON body sends the body and renders response"() {
        given:
        wireMock.stubFor(post(urlEqualTo("/create"))
                .withRequestBody(equalToJson('{"name":"test"}'))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody('[{"id":99,"name":"test"}]')))

        def yaml = """\
url: http://localhost:${port()}/create
method: POST
body: '{"name":"test"}'
"""
        when:
        def result = translator().translate(yaml, mdFile())

        then:
        result.isPresent()
        def doc = Jsoup.parse((result.get() as HtmlBlock).literal)
        doc.select("tbody tr").size() == 1
        doc.text().contains("99")
    }

    // -------------------------------------------------------------------------
    // 6. Custom headers
    // -------------------------------------------------------------------------

    def "custom request headers are sent with the HTTP request"() {
        given:
        wireMock.stubFor(get(urlEqualTo("/secure"))
                .withHeader("X-Api-Key", equalTo("secret123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody('[{"result":"ok"}]')))

        def yaml = """\
url: http://localhost:${port()}/secure
headers:
  X-Api-Key: secret123
"""
        when:
        def result = translator().translate(yaml, mdFile())

        then:
        result.isPresent()
        def doc = Jsoup.parse((result.get() as HtmlBlock).literal)
        doc.text().contains("ok")
    }

    // -------------------------------------------------------------------------
    // 7. HTTP 4xx
    // -------------------------------------------------------------------------

    def "HTTP 4xx response renders an error block containing the status code"() {
        given:
        wireMock.stubFor(get(urlEqualTo("/notfound"))
                .willReturn(aResponse().withStatus(404).withBody("Not Found")))

        def yaml = "url: http://localhost:${port()}/notfound\n"

        when:
        def result = translator().translate(yaml, null)

        then:
        result.isPresent()
        def html = (result.get() as HtmlBlock).literal
        html.contains("rest-block-error")
        html.contains("404")
    }

    // -------------------------------------------------------------------------
    // 8. HTTP 5xx
    // -------------------------------------------------------------------------

    def "HTTP 5xx response renders an error block"() {
        given:
        wireMock.stubFor(get(urlEqualTo("/broken"))
                .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")))

        def yaml = "url: http://localhost:${port()}/broken\n"

        when:
        def result = translator().translate(yaml, null)

        then:
        result.isPresent()
        (result.get() as HtmlBlock).literal.contains("rest-block-error")
        (result.get() as HtmlBlock).literal.contains("500")
    }

    // -------------------------------------------------------------------------
    // 9. Malformed JSON
    // -------------------------------------------------------------------------

    def "malformed JSON response body renders an error block"() {
        given:
        wireMock.stubFor(get(urlEqualTo("/bad-json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("this is not json")))

        def yaml = "url: http://localhost:${port()}/bad-json\n"

        when:
        def result = translator().translate(yaml, null)

        then:
        result.isPresent()
        (result.get() as HtmlBlock).literal.contains("rest-block-error")
    }

    // -------------------------------------------------------------------------
    // 10. Invalid JSONPath
    // -------------------------------------------------------------------------

    def "invalid JSONPath expression renders an error block"() {
        given:
        wireMock.stubFor(get(urlEqualTo("/ok"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody('[{"id":1}]')))

        def yaml = """\
url: http://localhost:${port()}/ok
jsonpath: "this is not [[ valid jsonpath"
"""
        when:
        def result = translator().translate(yaml, null)

        then:
        result.isPresent()
        (result.get() as HtmlBlock).literal.contains("rest-block-error")
    }

    // -------------------------------------------------------------------------
    // 11. Array of scalars
    // -------------------------------------------------------------------------

    def "array of scalar values is normalised to a single-column value table"() {
        given:
        wireMock.stubFor(get(urlEqualTo("/scalars"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody('["alpha","beta","gamma"]')))

        def yaml = "url: http://localhost:${port()}/scalars\n"

        when:
        def result = translator().translate(yaml, null)

        then:
        result.isPresent()
        def doc = Jsoup.parse((result.get() as HtmlBlock).literal)
        doc.select("th").collect { it.text() } == ["value"]
        doc.select("tbody tr").size() == 3
        doc.text().contains("alpha")
        doc.text().contains("gamma")
    }

    // -------------------------------------------------------------------------
    // 12. Single JSON object → one-row table
    // -------------------------------------------------------------------------

    def "single JSON object is rendered as a one-row table"() {
        given:
        wireMock.stubFor(get(urlEqualTo("/obj"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody('{"id":42,"status":"active"}')))

        def yaml = "url: http://localhost:${port()}/obj\n"

        when:
        def result = translator().translate(yaml, null)

        then:
        result.isPresent()
        def doc = Jsoup.parse((result.get() as HtmlBlock).literal)
        doc.select("tbody tr").size() == 1
        doc.text().contains("42")
        doc.text().contains("active")
    }

    // -------------------------------------------------------------------------
    // 13. Scalar JSONPath result
    // -------------------------------------------------------------------------

    def "scalar JSONPath result is rendered as a single-cell table"() {
        given:
        wireMock.stubFor(get(urlEqualTo("/scalar-path"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody('{"count":7}')))

        def yaml = """\
url: http://localhost:${port()}/scalar-path
jsonpath: "\$.count"
"""
        when:
        def result = translator().translate(yaml, null)

        then:
        result.isPresent()
        def doc = Jsoup.parse((result.get() as HtmlBlock).literal)
        doc.select("tbody tr").size() == 1
        doc.text().contains("7")
    }

    // -------------------------------------------------------------------------
    // 14. Nested object cell → nested table
    // -------------------------------------------------------------------------

    def "nested object cell values are rendered as nested HTML tables"() {
        given:
        wireMock.stubFor(get(urlEqualTo("/nested"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody('[{"id":1,"address":{"city":"London","zip":"EC1A"}}]')))

        def yaml = "url: http://localhost:${port()}/nested\n"

        when:
        def result = translator().translate(yaml, null)

        then:
        result.isPresent()
        def doc = Jsoup.parse((result.get() as HtmlBlock).literal)
        doc.select("table.rest-block-nested-table").size() >= 1
        doc.text().contains("London")
    }

    // -------------------------------------------------------------------------
    // 15. Nested array cell → nested table
    // -------------------------------------------------------------------------

    def "nested array cell values are rendered as nested HTML tables"() {
        given:
        wireMock.stubFor(get(urlEqualTo("/nested-arr"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody('[{"id":1,"tags":["java","spring"]}]')))

        def yaml = "url: http://localhost:${port()}/nested-arr\n"

        when:
        def result = translator().translate(yaml, null)

        then:
        result.isPresent()
        def doc = Jsoup.parse((result.get() as HtmlBlock).literal)
        doc.select("table.rest-block-nested-table").size() >= 1
        doc.text().contains("java")
    }

    // -------------------------------------------------------------------------
    // 16. Cache written on first call
    // -------------------------------------------------------------------------

    def "result is cached to a .output file on first call"() {
        given:
        wireMock.stubFor(get(urlEqualTo("/cache-test"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody('[{"id":1}]')))

        def yaml = "url: http://localhost:${port()}/cache-test\n"
        def md = mdFile("cache-test.md")

        when:
        translator().translate(yaml, md)

        then:
        def cacheFiles = tempDir.toFile().listFiles().findAll { it.name.endsWith(".output") }
        cacheFiles.size() == 1
    }

    // -------------------------------------------------------------------------
    // 17. Cache read on second call (no HTTP request)
    // -------------------------------------------------------------------------

    def "cached .output file is returned on the second call without making an HTTP request"() {
        given:
        wireMock.stubFor(get(urlEqualTo("/cache-hit"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody('[{"id":1}]')))

        def yaml = "url: http://localhost:${port()}/cache-hit\n"
        def md = mdFile("cache-hit.md")
        def t = translator()

        // Prime the cache
        t.translate(yaml, md)
        wireMock.resetAll()

        when:
        def result = t.translate(yaml, md)

        then:
        result.isPresent()
        // WireMock received exactly 1 request total (the first call)
        wireMock.getAllServeEvents().size() == 0
    }

    // -------------------------------------------------------------------------
    // 18. number-format column-format
    // -------------------------------------------------------------------------

    def "number-format column-format is applied to numeric cells"() {
        given:
        wireMock.stubFor(get(urlEqualTo("/prices"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody('[{"price":1234567.891}]')))

        def yaml = """\
url: http://localhost:${port()}/prices
column-formats:
  price:
    number-format: "#,##0.00"
"""
        when:
        def result = translator().translate(yaml, null)

        then:
        result.isPresent()
        def html = (result.get() as HtmlBlock).literal
        html.contains("1,234,567.89")
    }

    // -------------------------------------------------------------------------
    // 19. JTE template output
    // -------------------------------------------------------------------------

    def "jte output template is rendered with rows variable"() {
        given:
        wireMock.stubFor(get(urlEqualTo("/tpl"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody('[{"name":"Alice"}]')))

        def yaml = """\
url: http://localhost:${port()}/tpl
output:
  template-type: jte
  template: |
    @param java.util.List<java.util.Map<String,Object>> rows
    @for(var r : rows)<span>\${String.valueOf(r.get("name"))}</span>@endfor
"""
        when:
        def result = translator().translate(yaml, null)

        then:
        result.isPresent()
        (result.get() as HtmlBlock).literal.contains("Alice")
    }

    // -------------------------------------------------------------------------
    // 20. Null markdownFile → no caching but still renders
    // -------------------------------------------------------------------------

    def "null markdownFile disables caching but still renders"() {
        given:
        wireMock.stubFor(get(urlEqualTo("/no-cache"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody('[{"id":1}]')))

        def yaml = "url: http://localhost:${port()}/no-cache\n"

        when:
        def result = translator().translate(yaml, null)

        then:
        result.isPresent()
        result.get() instanceof HtmlBlock
        tempDir.toFile().listFiles().findAll { it.name.endsWith(".output") }.size() == 0
    }

    // -------------------------------------------------------------------------
    // 21. Missing url field
    // -------------------------------------------------------------------------

    def "missing url field in YAML renders an error block"() {
        given:
        def yaml = "method: GET\n"

        when:
        def result = translator().translate(yaml, null)

        then:
        result.isPresent()
        (result.get() as HtmlBlock).literal.contains("rest-block-error")
        (result.get() as HtmlBlock).literal.contains("url")
    }

    // -------------------------------------------------------------------------
    // 22. Blank url field
    // -------------------------------------------------------------------------

    def "blank url field in YAML renders an error block"() {
        given:
        def yaml = "url: '   '\n"

        when:
        def result = translator().translate(yaml, null)

        then:
        result.isPresent()
        (result.get() as HtmlBlock).literal.contains("rest-block-error")
    }

    // -------------------------------------------------------------------------
    // 23. normalizeToRows unit tests
    // -------------------------------------------------------------------------

    def "normalizeToRows wraps a plain Map in a single-element list"() {
        when:
        def rows = translator().normalizeToRows([id: 1, name: "x"])

        then:
        rows.size() == 1
        rows[0]["id"] == 1
    }

    def "normalizeToRows wraps scalars in value-keyed maps"() {
        when:
        def rows = translator().normalizeToRows(["a", "b", "c"])

        then:
        rows.size() == 3
        rows[0]["value"] == "a"
    }

    def "normalizeToRows wraps a bare scalar in a single-row list"() {
        when:
        def rows = translator().normalizeToRows(42)

        then:
        rows.size() == 1
        rows[0]["value"] == "42"
    }

    def "normalizeToRows returns empty list for null input"() {
        when:
        def rows = translator().normalizeToRows(null)

        then:
        rows.isEmpty()
    }
}