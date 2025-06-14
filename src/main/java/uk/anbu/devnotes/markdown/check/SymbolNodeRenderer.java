package uk.anbu.devnotes.markdown.check;

import org.commonmark.node.Node;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlNodeRendererFactory;
import org.commonmark.renderer.html.HtmlWriter;
import org.commonmark.renderer.NodeRenderer;

import java.util.Collections;
import java.util.Set;

public class SymbolNodeRenderer implements NodeRenderer {

    private final HtmlWriter html;

    public SymbolNodeRenderer(HtmlNodeRendererContext context) {
        this.html = context.getWriter();
    }

    @Override
    public Set<Class<? extends Node>> getNodeTypes() {
        return Collections.singleton(SymbolNode.class);
    }

    @Override
    public void render(Node node) {
        SymbolNode symbol = (SymbolNode) node;
        switch (symbol.getType()) {
            case CHECK:
                html.text("✓"); // U+2713
                break;
            case CROSS:
                html.text("✗"); // U+2717
                break;
        }
    }

    public static class Factory implements HtmlNodeRendererFactory {
        @Override
        public NodeRenderer create(HtmlNodeRendererContext context) {
            return new SymbolNodeRenderer(context);
        }
    }
}
