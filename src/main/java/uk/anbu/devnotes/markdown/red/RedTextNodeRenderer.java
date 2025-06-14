package uk.anbu.devnotes.markdown.red;

import org.commonmark.node.Node;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlNodeRendererFactory;
import org.commonmark.renderer.html.HtmlWriter;
import org.commonmark.renderer.NodeRenderer;

import java.util.Collections;
import java.util.Set;

public class RedTextNodeRenderer implements NodeRenderer {
    private final HtmlWriter html;

    public RedTextNodeRenderer(HtmlNodeRendererContext context) {
        this.html = context.getWriter();
    }

    @Override
    public Set<Class<? extends Node>> getNodeTypes() {
        return Collections.singleton(RedTextNode.class);
    }

    @Override
    public void render(Node node) {
        RedTextNode red = (RedTextNode) node;
        html.tag("span", Collections.singletonMap("class", "color-red"));
        html.text(red.getLiteral());
        html.tag("/span");
    }

    public static class Factory implements HtmlNodeRendererFactory {
        @Override
        public NodeRenderer create(HtmlNodeRendererContext context) {
            return new RedTextNodeRenderer(context);
        }
    }
}
