package uk.anbu.devnotes.markdown.check;

import org.commonmark.node.Node;
import org.commonmark.node.Text;

public class SymbolTransformer {

    public static void transform(Node node) {
        while (node != null) {
            Node firstChild = node.getFirstChild();

            if (node instanceof Text) {
                String literal = ((Text) node).getLiteral();
                Text newText = new Text(literal);
                boolean replaced = false;
                if (literal.contains("[-v-]")) {
                    newText = new Text(newText.getLiteral().replace("[-v-]", "✓"));
                    replaced = true;
                }
                if (literal.contains("[-x-]")) {
                    newText = new Text(newText.getLiteral().replace("[-x-]", "✗"));
                    replaced = true;
                }
                if (replaced) {
                    node.insertBefore(newText);
                    node.unlink();
                    node = newText;
                }
            }
            if (node.getFirstChild() != null) {
                transform(firstChild);
            }
            node = node.getNext();
        }
    }
}
