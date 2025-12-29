package uk.anbu.devnotes.markdown.red;

import lombok.Getter;
import org.commonmark.node.CustomNode;

@Getter
public class RedTextNode extends CustomNode {
    private final String literal;

    public RedTextNode(String literal) {
        this.literal = literal;
    }

}