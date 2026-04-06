package uk.anbu.devnotes.module;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.Extension;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.image.attributes.ImageAttributesExtension;
import org.commonmark.ext.ins.InsExtension;
import org.commonmark.node.Image;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.AttributeProvider;
import org.commonmark.renderer.html.HtmlRenderer;
import uk.anbu.devnotes.markdown.FrontMatterParser;
import uk.anbu.devnotes.markdown.check.SymbolTransformer;
import uk.anbu.devnotes.markdown.code.CodeBlockTransformer;
import uk.anbu.devnotes.markdown.code.DatabaseMetadataBlockTranslator;
import uk.anbu.devnotes.markdown.code.datablock.ParameterRegistry;
import uk.anbu.devnotes.markdown.groovy.GroovyInlineNodeRenderer;
import uk.anbu.devnotes.markdown.groovy.GroovyInlineTransformer;
import uk.anbu.devnotes.markdown.image.LocalImageTransformer;
import uk.anbu.devnotes.markdown.link.LocalLinkTransformer;
import uk.anbu.devnotes.markdown.red.RedTextNodeRenderer;
import uk.anbu.devnotes.markdown.red.RedTextTransformer;
import uk.anbu.devnotes.module.sql.SqlExecutor;
import uk.anbu.devnotes.service.ConfigService;
import uk.anbu.devnotes.service.DatasourceConfigResolver;
import uk.anbu.devnotes.service.ExchangeRateService;
import uk.anbu.devnotes.types.Markdown;
import uk.anbu.devnotes.types.MarkdownFile;
import uk.anbu.devnotes.types.MarkdownRenderResult;

import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class MarkdownRenderer {

    private final GroovyRenderer groovyRenderer;
    private final DatasourceConfigResolver dataSourceConfigResolver;
    private final SqlExecutor sqlExecutor;
    private final ConfigService configService;
    private final DatabaseMetadataBlockTranslator databaseMetadataBlockTranslator;
    private final ExchangeRateService exchangeRateService;

    public MarkdownRenderResult convertMarkdown(Markdown markdown, MarkdownFile markdownFile, Map<String, String> queryParams) {
        String markdownText = markdown.text();
        List<Extension> extensions = List.of(TablesExtension.create(),
                StrikethroughExtension.create(),
                ImageAttributesExtension.create(),
                InsExtension.create(),
                YamlFrontMatterExtension.create());
        Parser parser = Parser.builder()
                .extensions(extensions)
                .build();
        Node document = parser.parse(markdownText);
        var frontMatter = FrontMatterParser.parse(document);
        var parameterRegistry = new ParameterRegistry();
        // Seed registry with query params first so they can override later values
        if (queryParams != null && !queryParams.isEmpty()) {
            queryParams.forEach(parameterRegistry::put);
        }
        CodeBlockTransformer.builder()
                .markdownFile(markdownFile)
                .sqlExecutor(sqlExecutor)
                .groovyRenderer(groovyRenderer)
                .dataSourceConfigResolver(dataSourceConfigResolver)
                .configService(configService)
                .parameterRegistry(parameterRegistry)
                .databaseMetadataBlockTranslator(databaseMetadataBlockTranslator)
                .exchangeRateService(exchangeRateService)
                .build()
                .transform(document);
        LocalLinkTransformer.transform(document);
        RedTextTransformer.transform(document);
        GroovyInlineTransformer.transform(document);
        SymbolTransformer.transform(document);
        LocalImageTransformer.builder()
                .markdownFile(markdownFile)
                .build()
                .transform(document);
        HtmlRenderer renderer = HtmlRenderer.builder()
                .extensions(extensions)
                .nodeRendererFactory(new RedTextNodeRenderer.Factory())
                .nodeRendererFactory(new GroovyInlineNodeRenderer.Factory())
                .attributeProviderFactory(context -> new ImageAttributeProvider())
                .build();
        return new MarkdownRenderResult(renderer.render(document), frontMatter);
    }

    public static class ImageAttributeProvider implements AttributeProvider {
        @Override
        public void setAttributes(Node node, String tagName, Map<String, String> attributes) {
            if (node instanceof Image) {
                attributes.put("class", "border");
            }
        }
    }

}
