package uk.anbu.devnotes.markdown.code;

import lombok.RequiredArgsConstructor;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.Node;

import java.util.Optional;

import static j2html.TagCreator.div;

@RequiredArgsConstructor
public class MermaidBlockTranslator {

    public Optional<Node> renderDataBlock(String mermaidCode) {
        HtmlBlock mermaidBlockNode = new HtmlBlock();
        mermaidBlockNode.setLiteral(
                div().withClass("mermaid").withText(mermaidCode).render()
        );
        return Optional.of(mermaidBlockNode);
    }
}
