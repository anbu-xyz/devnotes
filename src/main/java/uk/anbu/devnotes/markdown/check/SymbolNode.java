package uk.anbu.devnotes.markdown.check;

import org.commonmark.node.CustomNode;

public class SymbolNode extends CustomNode {
    public enum Type {
        CHECK, CROSS
    }

    private final Type type;

    public SymbolNode(Type type) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }
}
