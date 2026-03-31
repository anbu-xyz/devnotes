package uk.anbu.devnotes.markdown.groovy;

import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import uk.anbu.devnotes.util.GroovyShellRunner;

/**
 * Walks the commonmark AST and replaces every {@code [groovy]expression[/groovy]} pattern found
 * inside {@link Text} nodes with a {@link GroovyInlineNode} whose result is the
 * {@code toString()} of the evaluated Groovy expression.  Evaluation errors produce an error
 * node that renders as a warning indicator rather than throwing.
 *
 * <p>Usage: call {@code GroovyInlineTransformer.transform(document)} after parsing and before
 * rendering.
 */
@Slf4j
public class GroovyInlineTransformer {

    private static final String OPEN = "[groovy]";
    private static final String CLOSE = "[/groovy]";

    public static void transform(Node node) {
        var current = node.getFirstChild();
        while (current != null) {
            var next = current.getNext();
            if (current instanceof Text textNode) {
                var literal = textNode.getLiteral();
                if (literal.contains(OPEN)) {
                    processInlineExpressions(textNode, literal);
                    textNode.unlink();
                }
            } else {
                transform(current);
            }
            current = next;
        }
    }

    private static void processInlineExpressions(Node referenceNode, String text) {
        var pos = 0;
        var start = text.indexOf(OPEN, pos);
        while (start != -1) {
            if (start > pos) {
                referenceNode.insertBefore(new Text(text.substring(pos, start)));
            }
            var end = text.indexOf(CLOSE, start + OPEN.length());
            if (end == -1) {
                // No closing }}, treat remainder as literal text
                referenceNode.insertBefore(new Text(text.substring(start)));
                return;
            }
            var expression = text.substring(start + OPEN.length(), end);
            referenceNode.insertBefore(evaluateExpression(expression));
            pos = end + CLOSE.length();
            start = text.indexOf(OPEN, pos);
        }
        if (pos < text.length()) {
            referenceNode.insertBefore(new Text(text.substring(pos)));
        }
    }

    private static GroovyInlineNode evaluateExpression(String expression) {
        try {
            log.debug("Evaluating inline Groovy expression: {}", expression);
            var result = GroovyShellRunner.execute(expression);
            return GroovyInlineNode.success(result);
        } catch (Exception e) {
            log.warn("Error evaluating inline Groovy expression '{}': {}", expression, e.getMessage());
            return GroovyInlineNode.error(e.getMessage());
        }
    }
}

