package uk.anbu.devnotes.markdown.groovy

import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import spock.lang.Specification

import java.time.LocalDate

class GroovyInlineTransformerSpec extends Specification {

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private static String renderMarkdown(String mdText) {
        def parser = Parser.builder().build()
        def doc = parser.parse(mdText)
        GroovyInlineTransformer.transform(doc)
        def renderer = HtmlRenderer.builder()
                .nodeRendererFactory(new GroovyInlineNodeRenderer.Factory())
                .build()
        return renderer.render(doc)
    }

    // -------------------------------------------------------------------------
    // 1. Basic evaluation
    // -------------------------------------------------------------------------

    def "simple arithmetic expression is evaluated and rendered in a span"() {
        when:
        def html = renderMarkdown("Result: [groovy]1 + 1[/groovy]")

        then:
        html.contains('<span class="groovy-inline">2</span>')
    }

    def "LocalDate.now() expression renders today's date"() {
        when:
        def html = renderMarkdown("Today is [groovy]LocalDate.now()[/groovy].")

        then:
        html.contains('<span class="groovy-inline">' + LocalDate.now().toString() + '</span>')
    }

    def "string expression is rendered verbatim"() {
        when:
        def html = renderMarkdown('Hello [groovy]"world"[/groovy]')

        then:
        html.contains('<span class="groovy-inline">world</span>')
    }

    // -------------------------------------------------------------------------
    // 2. Multiple expressions in one text node
    // -------------------------------------------------------------------------

    def "two expressions on the same line are both evaluated"() {
        when:
        def html = renderMarkdown("[groovy]1 + 1[/groovy] and [groovy]2 + 2[/groovy]")

        then:
        html.contains('<span class="groovy-inline">2</span>')
        html.contains('<span class="groovy-inline">4</span>')
    }

    def "literal text surrounding expressions is preserved"() {
        when:
        def html = renderMarkdown("before [groovy]3 * 3[/groovy] after")

        then:
        html.contains('before')
        html.contains('<span class="groovy-inline">9</span>')
        html.contains('after')
    }

    // -------------------------------------------------------------------------
    // 3. Error handling
    // -------------------------------------------------------------------------

    def "division by zero produces an error span with warning icon"() {
        when:
        def html = renderMarkdown("bad: [groovy]1/0[/groovy]")

        then:
        html.contains('class="groovy-inline-error"')
        html.contains('⚠')
        !html.contains('class="groovy-inline">')
    }

    def "error span carries a title attribute with the error message"() {
        when:
        def html = renderMarkdown("bad: [groovy]1/0[/groovy]")

        then:
        html.contains('title=')
    }

    def "undefined variable produces an error span"() {
        when:
        def html = renderMarkdown("[groovy]undefinedVar[/groovy]")

        then:
        html.contains('class="groovy-inline-error"')
        html.contains('⚠')
    }

    // -------------------------------------------------------------------------
    // 4. Edge cases
    // -------------------------------------------------------------------------

    def "plain text without [groovy] is unchanged"() {
        when:
        def html = renderMarkdown("just plain text")

        then:
        html.contains('just plain text')
        !html.contains('groovy-inline')
    }

    def "unclosed [groovy] is left as literal text"() {
        when:
        def html = renderMarkdown("broken [groovy]1 + 1")

        then:
        html.contains('[groovy]1 + 1')
        !html.contains('groovy-inline')
    }

    def "expression inside a heading is evaluated"() {
        when:
        def html = renderMarkdown("# Version [groovy]2 * 3[/groovy]")

        then:
        html.contains('<span class="groovy-inline">6</span>')
    }

    def "expression inside bold text is evaluated"() {
        when:
        def html = renderMarkdown("**bold [groovy]7 + 8[/groovy]**")

        then:
        html.contains('<span class="groovy-inline">15</span>')
    }

    def "expression inside a backtick code span is NOT evaluated"() {
        when:
        // commonmark parses backtick content as a Code node, not Text, so [groovy] inside should be inert
        def html = renderMarkdown("`[groovy]1 + 1[/groovy]`")

        then:
        html.contains('[groovy]1 + 1[/groovy]')
        !html.contains('groovy-inline')
    }

    def "html special characters in the result are escaped"() {
        // The & in the result must be rendered as &amp; so it does not break the HTML document
        when:
        def html = renderMarkdown('[groovy]"AT&T"[/groovy]')

        then:
        html.contains('<span class="groovy-inline">AT&amp;T</span>')
        !html.contains('AT&T<')
    }

    def "expression returning null renders as empty string"() {
        when:
        def html = renderMarkdown("[groovy]null[/groovy]")

        then:
        html.contains('<span class="groovy-inline"></span>')
    }
}