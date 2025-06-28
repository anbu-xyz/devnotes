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

    def "should render HTML output"() {
        given:
        def codeBlock = new FencedCodeBlock()
        codeBlock.setLiteral('"<div>Hello</div>"')
        def cacheFile = tempDir.resolve("test-cache.txt")

        when:
        def result = groovyRenderer.renderResult(codeBlock, cacheFile.toString(), "groovy:html")

        then:
        result.isPresent()
        result.get() instanceof HtmlBlock
        (result.get() as HtmlBlock).literal == "<div>Hello</div>"
    }

    def "should render text output"() {
        given:
        def codeBlock = new FencedCodeBlock()
        codeBlock.setLiteral('"Hello World"')
        def cacheFile = tempDir.resolve("test-cache.txt")

        when:
        def result = groovyRenderer.renderResult(codeBlock, cacheFile.toString(), "groovy:text")

        then:
        result.isPresent()
        result.get() instanceof Text
        (result.get() as Text).literal == "Hello World"
    }

    def "should render code block output"() {
        given:
        def codeBlock = new FencedCodeBlock()
        codeBlock.setLiteral('"Some code"')
        def cacheFile = tempDir.resolve("test-cache.txt")

        when:
        def result = groovyRenderer.renderResult(codeBlock, cacheFile.toString(), "groovy:code-block")

        then:
        result.isPresent()
        result.get() instanceof FencedCodeBlock
        (result.get() as FencedCodeBlock).literal == "Some code"
        (result.get() as FencedCodeBlock).info == "text"
    }

    def "should render CSV table without header"() {
        given:
        def codeBlock = new FencedCodeBlock()
        codeBlock.setLiteral('"""1,2,3\n4,5,6"""')
        def cacheFile = tempDir.resolve("test-cache.txt")

        when:
        def result = groovyRenderer.renderResult(codeBlock, cacheFile.toString(), "groovy:csv-table")

        then:
        result.isPresent()
        result.get() instanceof HtmlBlock
        (result.get() as HtmlBlock).literal == "<table><tr><td>1</td><td>2</td><td>3</td></tr><tr><td>4</td><td>5</td><td>6</td></tr></table>"
    }

    def "should render CSV table with header"() {
        given:
        def codeBlock = new FencedCodeBlock()
        codeBlock.setLiteral('"""A,B,C\n1,2,3"""')
        def cacheFile = tempDir.resolve("test-cache.txt")

        when:
        def result = groovyRenderer.renderResult(codeBlock, cacheFile.toString(), "groovy:csv-table-with-header")

        then:
        result.isPresent()
        result.get() instanceof HtmlBlock
        (result.get() as HtmlBlock).literal == "<table><tr><th>A</th><th>B</th><th>C</th></tr><tr><td>1</td><td>2</td><td>3</td></tr></table>"
    }

    def "should handle config parameters"() {
        given:
        def codeBlock = new FencedCodeBlock()
        codeBlock.setLiteral('"Hello"')
        def cacheFile = tempDir.resolve("test-cache.txt")

        when:
        def result = groovyRenderer.renderResult(codeBlock, cacheFile.toString(), "groovy:text(cacheEnabled:false)")

        then:
        result.isPresent()
        result.get() instanceof Text
        (result.get() as Text).literal == "Hello"
    }

    def "should return error message for unknown target type"() {
        given:
        def codeBlock = new FencedCodeBlock()
        codeBlock.setLiteral('"Hello"')
        def cacheFile = tempDir.resolve("test-cache.txt")

        when:
        def result = groovyRenderer.renderResult(codeBlock, cacheFile.toString(), "groovy:unknown")

        then:
        result.isPresent()
        result.get() instanceof Text
        (result.get() as Text).literal.contains("Error: Unknown target type 'unknown'")
    }

    def "should handle missing closing parenthesis in config"() {
        given:
        def codeBlock = new FencedCodeBlock()
        codeBlock.setLiteral('"Hello"')
        def cacheFile = tempDir.resolve("test-cache.txt")

        when:
        def result = groovyRenderer.renderResult(codeBlock, cacheFile.toString(), "groovy:text(cacheEnabled:true")

        then:
        !result.isPresent()
    }
}
