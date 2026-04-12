package uk.anbu.devnotes.markdown.code;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Image;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import uk.anbu.devnotes.module.GroovyRenderer;
import uk.anbu.devnotes.service.ConfigService;
import uk.anbu.devnotes.service.DatasourceConfigResolver;
import uk.anbu.devnotes.service.ExchangeRateService;
import uk.anbu.devnotes.types.MarkdownFile;
import uk.anbu.devnotes.markdown.code.datablock.ParameterRegistry;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static uk.anbu.devnotes.util.FileBasedCache.generateCacheFileName;
import static uk.anbu.devnotes.util.FileBasedCache.generateHash;

@Slf4j
@Builder
public class CodeBlockTransformer {

    @Builder.Default
    private Integer codeBlockCounter = 0;
    private final MarkdownFile markdownFile;
    private final GroovyRenderer groovyRenderer;
    private final DatasourceConfigResolver dataSourceConfigResolver;
    private final ConfigService configService;
    private final ParameterRegistry parameterRegistry;
    private final DatabaseMetadataBlockTranslator databaseMetadataBlockTranslator;
    private final ExchangeRateService exchangeRateService;

    public void transform(Node current) {
        // If there is a next sibling, process it
        if (current instanceof FencedCodeBlock) {
            processFencedCodeBlock((FencedCodeBlock) current);
            // fenced code blocks cannot have children, so no need to process firstChild
        } else {
            log.trace("Not a fenced code block: {}. Ignored", current.getClass().getSimpleName());
            if (current.getFirstChild() != null) {
                // If there is a child, process it - depth-first traversal
                transform(current.getFirstChild());
            }
        }
        if (current.getNext() != null) {
            // If there is a next sibling, process it
            transform(current.getNext());
        }
    }

    private void processFencedCodeBlock(FencedCodeBlock codeBlock) {
        String codeType = codeBlock.getInfo();
        if ("groovy-exec".equals(codeType)) {
            String cacheFileName = generateCacheFileName(markdownFile, codeBlock.getLiteral());
            String groovyId = generateHash(codeBlock.getLiteral());
            var node = groovyRenderer.renderResultWrapped(codeBlock, cacheFileName, groovyId);
            if (node.isEmpty()) {
                codeBlock.insertBefore(new Text("Error: Unable to render Groovy result"));
            } else {
                codeBlock.insertAfter(node.get());
                codeBlock.setInfo("hidden-groovy-exec");
            }
        } else if (codeType.matches("^data$")) {
            renderDataBlock(codeBlock, markdownFile);
        } else if (codeType.matches("^parameter$")) {
            renderParameterBlock(codeBlock);
        } else if (codeType.matches("^mermaid$")) {
            renderMermaidBlock(codeBlock);
        } else if (codeType.matches("^plantuml\\(([^)]*)\\)$") || codeType.matches("^plantuml$")) {
            renderPlantUmlResult(codeBlock);
        } else if (codeType.equals("database-metadata")) {
            renderDatabaseMetadataBlock(codeBlock);
        } else if (codeType.equals("todo")) {
            renderTodoBlock(codeBlock);
        } else if (codeType.equals("rest")) {
            renderRestBlock(codeBlock, markdownFile);
        } else {
            log.debug("unhandled code type: {}, delegating to default handler", codeType);
        }
    }

    private void renderMermaidBlock(FencedCodeBlock codeBlock) {
        String mermaidCode = codeBlock.getLiteral();
        try {
            Optional<Node> newNodeToInsert = new MermaidBlockTranslator()
                    .renderDataBlock(mermaidCode);

            newNodeToInsert.ifPresent(codeBlock::insertBefore);

            // rename original info text from 'data(...)' to 'hidden-data' to hide it from rendering
            codeBlock.setInfo("hidden-mermaid");
        } catch (Exception e) {
            log.error("Error rendering Data result", e);
            Node newNodeToInsert = new Text("Error rendering SQL result: " + e.getMessage());
            codeBlock.insertBefore(newNodeToInsert);
        }
    }

    private void renderDatabaseMetadataBlock(FencedCodeBlock codeBlock) {
        String yaml = codeBlock.getLiteral();
        try {
            var translator = databaseMetadataBlockTranslator != null
                    ? databaseMetadataBlockTranslator
                    : new DatabaseMetadataBlockTranslator();
            translator.translate(yaml).ifPresent(codeBlock::insertBefore);
            codeBlock.setInfo("hidden-database-metadata");
        } catch (Exception e) {
            log.error("Error rendering database-metadata block", e);
            codeBlock.insertBefore(new Text("Error rendering database-metadata block: " + e.getMessage()));
        }
    }

    private void renderTodoBlock(FencedCodeBlock codeBlock) {
        try {
            new TodoBlockTranslator()
                    .translate(codeBlock.getLiteral())
                    .ifPresent(codeBlock::insertBefore);
            codeBlock.setInfo("hidden-todo");
        } catch (Exception e) {
            log.error("Error rendering todo block", e);
            codeBlock.insertBefore(new Text("Error rendering todo block: " + e.getMessage()));
        }
    }

    private void renderRestBlock(FencedCodeBlock codeBlock, MarkdownFile markdownFile) {
        try {
            new RestBlockTranslator()
                    .translate(codeBlock.getLiteral(), markdownFile)
                    .ifPresent(codeBlock::insertBefore);
            codeBlock.setInfo("hidden-rest");
        } catch (Exception e) {
            log.error("Error rendering rest block", e);
            codeBlock.insertBefore(new Text("Error rendering rest block: " + e.getMessage()));
        }
    }

    private void renderParameterBlock(FencedCodeBlock codeBlock) {
        String yaml = codeBlock.getLiteral();
        try {
            var translator = new ParameterBlockTranslator(parameterRegistry);
            Node newNodeToInsert = translator.renderParameterBlock(yaml);
            codeBlock.insertBefore(newNodeToInsert);
            codeBlock.setInfo("hidden-parameter");
        } catch (Exception e) {
            log.error("Error rendering Parameter block", e);
            Node newNodeToInsert = new Text("Error rendering parameter block: " + e.getMessage());
            codeBlock.insertBefore(newNodeToInsert);
        }
    }

    private void renderDataBlock(FencedCodeBlock codeBlock, MarkdownFile markdownFile) {
        String dataConfig = codeBlock.getLiteral();
        try {
            var newNodeToInsert = new DataBlockTranslator(dataSourceConfigResolver, configService, parameterRegistry, exchangeRateService)
                    .renderDataBlock(dataConfig, markdownFile);
            newNodeToInsert.ifPresent(codeBlock::insertBefore);

            // rename original info text from 'data(...)' to 'hidden-data' to hide it from rendering
            codeBlock.setInfo("hidden-data");
        } catch (Exception e) {
            log.error("Error rendering Data result", e);
            Node newNodeToInsert = new Text("Error rendering SQL result: " + e.getMessage());
            codeBlock.insertBefore(newNodeToInsert);
        }
    }

    private void renderPlantUmlResult(FencedCodeBlock codeBlock) {
        var request = codeBlock.getLiteral();
        var urlEncodedRequest = URLEncoder.encode(request, StandardCharsets.UTF_8);
        String url = "/plantumlContent?content=" + urlEncodedRequest;
        log.info("Rendering plantuml: {}", url);
        var image = new Image(url, "plantuml");
        codeBlock.insertAfter(image);
        codeBlock.setInfo("hidden-plantuml");
    }
}
