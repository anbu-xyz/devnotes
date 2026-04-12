package uk.anbu.devnotes.module

import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HtmlBlock
import org.commonmark.node.Text
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.util.function.Supplier

class GroovyRendererSpec extends Specification {
    @TempDir
    Path tempDir

    GroovyRenderer groovyRenderer

    def setup() {
        groovyRenderer = new GroovyRenderer({ Optional.empty() } as Supplier<Optional<String>>)
    }

    // =========================================================================
    // YAML-header format
    // =========================================================================

    def "renders html output"() {
        given:
        def codeBlock = new FencedCodeBlock()
        codeBlock.setLiteral("output: html\n---\n\"<div>Hello</div>\"\n")
        def cacheFile = tempDir.resolve("html.txt")

        when:
        def result = groovyRenderer.renderResult(codeBlock, cacheFile.toString())

        then:
        result.isPresent()
        result.get() instanceof HtmlBlock
        (result.get() as HtmlBlock).literal == "<div>Hello</div>"
    }

    def "renders text output"() {
        given:
        def codeBlock = new FencedCodeBlock()
        codeBlock.setLiteral("output: text\n---\n\"Hello World\"\n")
        def cacheFile = tempDir.resolve("text.txt")

        when:
        def result = groovyRenderer.renderResult(codeBlock, cacheFile.toString())

        then:
        result.isPresent()
        result.get() instanceof Text
        (result.get() as Text).literal == "Hello World"
    }

    def "renders code-block output"() {
        given:
        def codeBlock = new FencedCodeBlock()
        codeBlock.setLiteral("output: code-block\n---\n\"Some code\"\n")
        def cacheFile = tempDir.resolve("code-block.txt")

        when:
        def result = groovyRenderer.renderResult(codeBlock, cacheFile.toString())

        then:
        result.isPresent()
        result.get() instanceof FencedCodeBlock
        (result.get() as FencedCodeBlock).literal == "Some code"
        (result.get() as FencedCodeBlock).info == "text"
    }

    def "renders CSV table output"() {
        given:
        def codeBlock = new FencedCodeBlock()
        codeBlock.setLiteral("output: csv-table\n---\n\"\"\"A,B\n1,2\"\"\"\n")
        def cacheFile = tempDir.resolve("csv.txt")

        when:
        def result = groovyRenderer.renderResult(codeBlock, cacheFile.toString())

        then:
        result.isPresent()
        result.get() instanceof HtmlBlock
        (result.get() as HtmlBlock).literal.contains("<td>A</td>")
    }

    def "renders CSV table with header output"() {
        given:
        def codeBlock = new FencedCodeBlock()
        codeBlock.setLiteral("output: csv-table-with-header\n---\n\"\"\"Name,Age\nAlice,30\"\"\"\n")
        def cacheFile = tempDir.resolve("csv-header.txt")

        when:
        def result = groovyRenderer.renderResult(codeBlock, cacheFile.toString())

        then:
        result.isPresent()
        result.get() instanceof HtmlBlock
        (result.get() as HtmlBlock).literal.contains("<th>Name</th>")
        (result.get() as HtmlBlock).literal.contains("<td>Alice</td>")
    }

    def "returns error message for unknown output type"() {
        given:
        def codeBlock = new FencedCodeBlock()
        codeBlock.setLiteral("output: unknown\n---\n\"Hello\"\n")
        def cacheFile = tempDir.resolve("unknown.txt")

        when:
        def result = groovyRenderer.renderResult(codeBlock, cacheFile.toString())

        then:
        result.isPresent()
        result.get() instanceof Text
        (result.get() as Text).literal.contains("Error: Unknown target type 'unknown'")
    }

    def "cache-enabled false disables caching"() {
        given:
        def codeBlock = new FencedCodeBlock()
        codeBlock.setLiteral("output: text\ncache-enabled: false\n---\n\"no cache\"\n")
        def cacheFile = tempDir.resolve("no-cache.txt")

        when:
        def result = groovyRenderer.renderResult(codeBlock, cacheFile.toString())

        then:
        result.isPresent()
        (result.get() as Text).literal == "no cache"
        !cacheFile.toFile().exists()
    }

    def "output field defaults to html when omitted"() {
        given:
        def codeBlock = new FencedCodeBlock()
        codeBlock.setLiteral("cache-enabled: false\n---\n\"<span>default</span>\"\n")
        def cacheFile = tempDir.resolve("default-output.txt")

        when:
        def result = groovyRenderer.renderResult(codeBlock, cacheFile.toString())

        then:
        result.isPresent()
        result.get() instanceof HtmlBlock
        (result.get() as HtmlBlock).literal == "<span>default</span>"
    }

    def "missing separator treats entire body as script with html default"() {
        given:
        def codeBlock = new FencedCodeBlock()
        codeBlock.setLiteral('"<i>fallback</i>"')
        def cacheFile = tempDir.resolve("no-sep.txt")

        when:
        def result = groovyRenderer.renderResult(codeBlock, cacheFile.toString())

        then:
        result.isPresent()
        (result.get() as HtmlBlock).literal == "<i>fallback</i>"
    }

    // =========================================================================
    // renderResultWrapped wrapper behaviour
    // =========================================================================

    def "renderResultWrapped includes groovy-block div with data-groovy-id"() {
        given:
        def codeBlock = new FencedCodeBlock()
        codeBlock.setLiteral("output: text\n---\n\"Hi\"\n")
        def cacheFile = tempDir.resolve("wrap-default.txt")

        when:
        def result = groovyRenderer.renderResultWrapped(codeBlock, cacheFile.toString(), "abc123")

        then:
        result.isPresent()
        def html = (result.get() as HtmlBlock).literal
        html.contains('class="groovy-block"')
        html.contains('data-groovy-id="abc123"')
        !html.contains('data-groovy-controls')
    }

    def "controls-enabled false sets data-groovy-controls attribute"() {
        given:
        def codeBlock = new FencedCodeBlock()
        codeBlock.setLiteral("output: text\ncontrols-enabled: false\n---\n\"Hi\"\n")
        def cacheFile = tempDir.resolve("no-controls.txt")

        when:
        def result = groovyRenderer.renderResultWrapped(codeBlock, cacheFile.toString(), "id42")

        then:
        result.isPresent()
        def html = (result.get() as HtmlBlock).literal
        html.contains('data-groovy-controls="false"')
    }

    def "controls-enabled defaults to true"() {
        given:
        def codeBlock = new FencedCodeBlock()
        codeBlock.setLiteral("output: html\n---\n\"<b>ok</b>\"\n")
        def cacheFile = tempDir.resolve("default-controls.txt")

        when:
        def result = groovyRenderer.renderResultWrapped(codeBlock, cacheFile.toString(), "id99")

        then:
        result.isPresent()
        def html = (result.get() as HtmlBlock).literal
        !html.contains('data-groovy-controls')
        html.contains('class="groovy-block"')
        html.contains('data-groovy-id="id99"')
    }

    def "cache-enabled false and controls-enabled false can be combined"() {
        given:
        def codeBlock = new FencedCodeBlock()
        codeBlock.setLiteral("output: text\ncache-enabled: false\ncontrols-enabled: false\n---\n\"Hi\"\n")
        def cacheFile = tempDir.resolve("no-cache-no-controls.txt")

        when:
        def result = groovyRenderer.renderResultWrapped(codeBlock, cacheFile.toString(), "abc123")

        then:
        result.isPresent()
        def html = (result.get() as HtmlBlock).literal
        html.contains('data-groovy-controls="false"')
        !cacheFile.toFile().exists()
    }

    // =========================================================================
    // parseYamlConfig unit tests
    // =========================================================================

    def "parseYamlConfig extracts output, cache-enabled and controls-enabled"() {
        when:
        def parsed = GroovyRenderer.parseYamlConfig("output: text\ncache-enabled: false\ncontrols-enabled: false\n---\nprintln 'hello'\n")

        then:
        parsed.targetType() == "text"
        parsed.script() == "println 'hello'\n"
        !parsed.config().cachingEnabled()
        !parsed.config().controlsEnabled()
    }

    def "parseYamlConfig defaults when separator is absent"() {
        when:
        def parsed = GroovyRenderer.parseYamlConfig("println 'hello'\n")

        then:
        parsed.targetType() == "html"
        parsed.script() == "println 'hello'\n"
        parsed.config().cachingEnabled()
        parsed.config().controlsEnabled()
    }

    def "parseYamlConfig defaults when YAML part is blank"() {
        when:
        def parsed = GroovyRenderer.parseYamlConfig("\n---\nprintln 'hello'\n")

        then:
        parsed.targetType() == "html"
        parsed.script() == "println 'hello'\n"
    }
}
