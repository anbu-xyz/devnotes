package uk.anbu.devnotes.markdown.groovy;

import lombok.Getter;
import org.commonmark.node.CustomNode;

@Getter
public class GroovyInlineNode extends CustomNode {

    private final String result;  // non-null when evaluation succeeded
    private final String error;   // non-null when evaluation failed

    private GroovyInlineNode(String result, String error) {
        this.result = result;
        this.error = error;
    }

    public static GroovyInlineNode success(String result) {
        return new GroovyInlineNode(result, null);
    }

    public static GroovyInlineNode error(String error) {
        return new GroovyInlineNode(null, error);
    }

    public boolean isError() {
        return error != null;
    }
}

