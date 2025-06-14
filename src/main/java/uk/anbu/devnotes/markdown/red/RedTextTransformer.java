package uk.anbu.devnotes.markdown.red;

import org.commonmark.node.*;

public class RedTextTransformer {

    public static void transform(Node node) {
        Node current = node.getFirstChild();
        while (current != null) {
            Node next = current.getNext();

            if (current instanceof Text) {
                String literal = ((Text) current).getLiteral();
                if (literal.contains("[red]") && literal.contains("[/red]")) {
                    Node parent = current.getParent();
                    // Remove the current text node from the tree
                    current.unlink();
                    // Process the text and append new nodes
                    processRedText(parent, literal, current);
                }
            } else {
                transform(current); // Recurse into children
            }

            current = next;
        }
    }

    private static void processRedText(Node parent, String text, Node referenceNode) {
        int pos = 0;
        while (pos < text.length()) {
            int start = text.indexOf("[red]", pos);
            if (start == -1) {
                parent.appendChild(new Text(text.substring(pos)));
                break;
            }

            // Add preceding plain text if any
            if (start > pos) {
                parent.appendChild(new Text(text.substring(pos, start)));
            }

            int end = text.indexOf("[/red]", start);
            if (end == -1) {
                // No closing tag, treat rest as plain text
                parent.appendChild(new Text(text.substring(start)));
                break;
            }

            // Extract red text
            String redContent = text.substring(start + 5, end);
            parent.appendChild(new RedNode(redContent));
            pos = end + 6;
        }
    }
}
