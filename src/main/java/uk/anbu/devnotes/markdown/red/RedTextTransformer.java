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
                    // Process the text and append new nodes
                    processRedText(current, literal);
                    // Remove the current text node from the tree
                    current.unlink();
                }
            } else {
                transform(current); // Recurse into children
            }

            current = next;
        }
    }

    private static void processRedText(Node referenceNode, String text) {
        int pos = 0;

        int start = text.indexOf("[red]");
        while(start != -1) {
            referenceNode.insertBefore(new Text(text.substring(pos, start)));
            int end = text.indexOf("[/red]", start);
            if (end == -1) {
                // No closing tag found, treat the rest as normal text
                referenceNode.insertBefore(new Text(text.substring(start)));
                return;
            }
            referenceNode.insertBefore(new RedTextNode(text.substring(start + "[red]".length(), end)));
            pos = end + "[/red]".length();
            start = text.indexOf("[red]", pos);
        }
        referenceNode.insertBefore(new Text(text.substring(pos)));
    }
}
