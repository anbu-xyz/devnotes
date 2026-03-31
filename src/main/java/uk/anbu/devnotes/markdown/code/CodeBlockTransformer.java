package uk.anbu.devnotes.markdown.code;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.Image;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import uk.anbu.devnotes.module.GroovyRenderer;
import uk.anbu.devnotes.module.sql.SqlExecutor;
import uk.anbu.devnotes.service.ConfigService;
import uk.anbu.devnotes.service.DatasourceConfigResolver;
import uk.anbu.devnotes.service.EncryptionService;
import uk.anbu.devnotes.service.ExchangeRateService;
import uk.anbu.devnotes.types.MarkdownFile;
import uk.anbu.devnotes.markdown.code.datablock.ParameterRegistry;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static uk.anbu.devnotes.util.FileBasedCache.generateCacheFileName;
import static uk.anbu.devnotes.util.FileBasedCache.generateHash;

@Slf4j
@Builder
public class CodeBlockTransformer {

    @Builder.Default
    private Integer codeBlockCounter = 0;
    private final MarkdownFile markdownFile;
    private final SqlExecutor sqlExecutor;
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
        // match codeType of format "groovy:targetType(config1:value1,config2:value2) or "groovy:targetType"
        if (codeType.matches("^groovy:([^(]+)\\(.*\\)$") || codeType.matches("^groovy:([^(]+)$")) {
            String cacheFileName = generateCacheFileName(markdownFile, codeBlock.getLiteral());
            String groovyId = generateHash(codeBlock.getLiteral());
            var node = groovyRenderer.renderResultWrapped(codeBlock, cacheFileName, codeType, groovyId);
            if (node.isEmpty()) {
                codeBlock.insertBefore(new Text("Error: Unable to render Groovy result"));
            } else {
                codeBlock.insertAfter(node.get());
                codeBlock.setInfo("hidden-groovy");
            }
        } else if (codeType.matches("^sql\\(([^)]+)\\)$")) {
            renderSqlResult(codeBlock, markdownFile, codeType);
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

    private void renderSqlResult(FencedCodeBlock codeBlock, MarkdownFile markdownFile, String codeType) {
        // codetype is of format sql(config1:value1,config2:value2)
        String configString = codeType.substring("sql(".length(), codeType.length() - 1);
        String[] configParts = configString.split(",");
        Map<String, String> configMap = new HashMap<>();
        for (String configKeyValue : configParts) {
            // if colon is not found, assume the configKeyValue is the datasource name
            if (configKeyValue.indexOf(':') == -1) {
                configMap.put("datasource", configKeyValue);
                continue;
            }
            // strip leading and trailing whitespace
            configKeyValue = configKeyValue.trim();
            String[] keyValue = configKeyValue.split(":");
            configMap.put(keyValue[0], keyValue[1]);
        }
        if (!configMap.containsKey("datasource")) {
            log.error("Error rendering SQL result: datasource not specified in config");
            return;
        }

        String sql = codeBlock.getLiteral();
        Node newNodeToInsert;
        var maxRows = readMaxRows(configMap);
        var dataSourceConfig = dataSourceConfigResolver.resolve(configMap.get("datasource"));
        if (dataSourceConfig == null) {
            newNodeToInsert = new Text("Error: DataSource '" + configMap.get("datasource") + "' not defined in config.");
        } else if (EncryptionService.isEncrypted(dataSourceConfig.password())
                && configService != null && !configService.isEncryptionKeySet()) {
            String dsName = configMap.get("datasource");
            String returnUrl = markdownFile != null
                    ? "/markdown?filename=" + URLEncoder.encode(markdownFile.fileName(), StandardCharsets.UTF_8)
                    : null;
            String href = "/config/encryption-key"
                    + (returnUrl != null ? "?returnTo=" + URLEncoder.encode(returnUrl, StandardCharsets.UTF_8) : "");
            HtmlBlock warning = new HtmlBlock();
            warning.setLiteral("<div class=\"enc-key-needed\"><i class=\"fas fa-lock\"></i>"
                    + " Datasource '" + dsName + "' has an encrypted password. "
                    + "<a href=\"" + href + "\">Enter encryption key</a></div>\n");
            newNodeToInsert = warning;
        } else {
            try {
                newNodeToInsert = processSqlCodeBlock(sql, dataSourceConfig, markdownFile, maxRows);
            } catch (Exception e) {
                log.error("Error rendering SQL result", e);
                newNodeToInsert = new Text("Error rendering SQL result: " + e.getMessage()
                        + " original SQL: " + sql);
            }
        }
        codeBlock.insertBefore(newNodeToInsert);

        // rename original info text from 'sql(...)' to 'hidden-sql' to hide it from rendering
        codeBlock.setInfo("hidden-sql");
    }

    private static int readMaxRows(Map<String, String> configMap) {
        if (configMap.get("max_rows") == null) {
            return 0;
        }
        try {
            return Integer.parseInt(configMap.get("max_rows"));
        } catch (NumberFormatException e) {
            log.warn("Invalid max_rows value in config, using default value");
            return 0;
        }
    }

    private HtmlBlock processSqlCodeBlock(String sql, ConfigService.DataSourceConfig dataSourceConfig,
                                          MarkdownFile markdownFile, int maxRows) {

        List<String> parameterNames = extractParameterNames(sql);
        Map<String, String> parameterValues = new LinkedHashMap<>();

        if (!parameterNames.isEmpty()) {
            // TODO: Implement user input for parameter values
            // For now, we'll use placeholder values
            for (String param : parameterNames) {
                if (parameterRegistry != null) {
                    var sp = parameterRegistry.get(param);
                    if (sp != null) {
                        parameterValues.put(param, sp.toString());
                        continue;
                    }
                }
                parameterValues.put(param, "");
            }
        }

        var request = new SqlExecutor.JsonGenerationRequest(dataSourceConfig, sql, parameterValues,
                markdownFile, maxRows, false);
        var outputPath = sqlExecutor.renderResultAsJsonFile(request);

        return renderSqlResultTable(sql, outputPath, parameterValues, dataSourceConfig.name(),
                markdownFile);
    }

    private List<String> extractParameterNames(String sql) {
        List<String> parameterNames = new ArrayList<>();
        Pattern pattern = Pattern.compile(":(\\w+)");
        Matcher matcher = pattern.matcher(sql);
        while (matcher.find()) {
            parameterNames.add(matcher.group(1));
        }
        return parameterNames;
    }

    private HtmlBlock renderSqlResultTable(String sqlText, Path outputPath, Map<String, String> parameterValues,
                                           String dataSourceName, MarkdownFile markdownFile) {
        var request = new SqlExecutor.HtmlTableRequest(sqlText, outputPath, parameterValues, dataSourceName,
                markdownFile, codeBlockCounter);
        codeBlockCounter++;
        String tableString = sqlExecutor.convertToHtmlTable(request);
        HtmlBlock htmlBlock = new HtmlBlock();
        htmlBlock.setLiteral(tableString);
        return htmlBlock;
    }

}
