package uk.anbu.devnotes.markdown.check;

import org.commonmark.node.Node;
import org.commonmark.node.Text;

public class SymbolTransformer {

    public static void transform(Node node) {
        Node current = node.getFirstChild();
        while (current != null) {
            Node next = current.getNext();

            if (current instanceof Text) {
                String literal = ((Text) current).getLiteral();
                if (literal.contains("[v]") || literal.contains("[x]")) {
                    Node parent = current.getParent();
                    current.unlink();
                    insertSymbolNodes(parent, literal);
                }
            } else {
                transform(current);
            }

            current = next;
        }
    }

    private static void insertSymbolNodes(Node parent, String text) {
        int pos = 0;
        while (pos < text.length()) {
            int indexV = text.indexOf("[v]", pos);
            int indexX = text.indexOf("[x]", pos);

            // Find the next symbol to replace
            int index;
            SymbolNode.Type type;

            if (indexV == -1 && indexX == -1) {
                index = -1;
                type = null;
            } else if (indexV != -1 && (indexX == -1 || indexV < indexX)) {
                index = indexV;
                type = SymbolNode.Type.CHECK;
            } else {
                index = indexX;
                type = SymbolNode.Type.CROSS;
            }

            if (index == -1) {
                parent.appendChild(new Text(text.substring(pos)));
                break;
            }

            // Handle escape with backslash
            if (index > 0 && text.charAt(index - 1) == '\\') {
                // Append text up to and including the backslash
                parent.appendChild(new Text(text.substring(pos, index)));
                // Append literal [v] or [x] without the backslash
                parent.appendChild(new Text(text.substring(index, index + 3)));
                pos = index + 3;
            } else {
                if (index > pos) {
                    parent.appendChild(new Text(text.substring(pos, index)));
                }
                parent.appendChild(new SymbolNode(type));
                pos = index + 3; // Move past `[v]` or `[x]`
            }
        }
    }
}
