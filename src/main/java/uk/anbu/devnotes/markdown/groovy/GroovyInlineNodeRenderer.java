package uk.anbu.devnotes.markdown.groovy;

import org.commonmark.node.Node;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlNodeRendererFactory;
import org.commonmark.renderer.html.HtmlWriter;

import java.util.Map;
import java.util.Set;

public class GroovyInlineNodeRenderer implements NodeRenderer {

    private final HtmlWriter html;

    public GroovyInlineNodeRenderer(HtmlNodeRendererContext context) {
        this.html = context.getWriter();
    }

    @Override
    public Set<Class<? extends Node>> getNodeTypes() {
        return Set.of(GroovyInlineNode.class);
    }

    @Override
    public void render(Node node) {
        var groovyNode = (GroovyInlineNode) node;
        if (groovyNode.isError()) {
            html.tag("span", Map.of("class", "groovy-inline-error", "title", groovyNode.getError()));
            html.text("⚠");
            html.tag("/span");
        } else {
            html.tag("span", Map.of("class", "groovy-inline"));
            html.text(groovyNode.getResult());
            html.tag("/span");
        }
    }

    public static class Factory implements HtmlNodeRendererFactory {
        @Override
        public NodeRenderer create(HtmlNodeRendererContext context) {
            return new GroovyInlineNodeRenderer(context);
        }
    }
}

