package uk.anbu.devnotes.markdown.check;

import org.commonmark.node.Node;
import org.commonmark.node.Text;
import java.util.Map;

public class SymbolTransformer {
    private static final Map<String, String> replacements = Map.of(
            "[-v-]", "✓",
            "[-x-]", "✗",
            "[-w-]", "⚠",
            "[-s-]", "★",
            "[-a-]", "➤"
    );

    public static void transform(Node node) {
        while (node != null) {
            Node firstChild = node.getFirstChild();

            if (node instanceof Text) {
                Text newText = new Text(((Text) node).getLiteral());
                boolean replaced = false;

                for (Map.Entry<String, String> replacement : replacements.entrySet()) {
                    String originalText = replacement.getKey();
                    String replacementText = replacement.getValue();

                    if (newText.getLiteral().contains(originalText)) {
                        newText = new Text(newText.getLiteral().replace(originalText, replacementText));
                        replaced = true;
                    }
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
