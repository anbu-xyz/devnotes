package uk.anbu.devnotes.markdown;

import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import uk.anbu.devnotes.types.FrontMatter;

import java.util.List;

public final class FrontMatterParser {

    private FrontMatterParser() {
    }

    /**
     * Extracts YAML front-matter from an already-parsed commonmark AST document.
     * The document must have been built with {@link YamlFrontMatterExtension} enabled.
     */
    public static FrontMatter parse(Node document) {
        var visitor = new YamlFrontMatterVisitor();
        document.accept(visitor);
        return FrontMatter.from(visitor.getData());
    }

    /**
     * Parses YAML front-matter from raw markdown text.
     * Uses a minimal parser (only the front-matter extension) so it is cheap to call
     * when a full render is not needed (e.g. to build the page {@code <title>} tag).
     */
    public static FrontMatter parseText(String markdownText) {
        var extensions = List.of(YamlFrontMatterExtension.create());
        var parser = Parser.builder().extensions(extensions).build();
        var document = parser.parse(markdownText);
        return parse(document);
    }
}