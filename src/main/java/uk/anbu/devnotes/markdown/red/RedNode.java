package uk.anbu.devnotes.markdown.red;

import org.commonmark.node.CustomNode;

public class RedNode extends CustomNode {
    private final String literal;

    public RedNode(String literal) {
        this.literal = literal;
    }

    public String getLiteral() {
        return literal;
    }
}