package uk.anbu.devnotes.markdown.red;

import org.commonmark.node.CustomNode;

public class RedTextNode extends CustomNode {
    private final String literal;

    public RedTextNode(String literal) {
        this.literal = literal;
    }

    public String getLiteral() {
        return literal;
    }
}