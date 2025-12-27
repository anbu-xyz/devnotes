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
import uk.anbu.devnotes.types.MarkdownFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static uk.anbu.devnotes.util.FileBasedCache.generateCacheFileName;

@Slf4j
@Builder
public class CodeBlockTransformer {

    @Builder.Default
    private Integer codeBlockCounter = 0;
    private final MarkdownFile markdownFile;
    private final SqlExecutor sqlExecutor;
    private final GroovyRenderer groovyRenderer;
    private final Function<String, ConfigService.DataSourceConfig> dataSourceConfigResolver;

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
            var node = groovyRenderer.renderResult(codeBlock, cacheFileName, codeType);
            if (node.isEmpty()) {
                codeBlock.insertBefore(new Text("Error: Unable to render Groovy result"));
            } else {
                codeBlock.insertAfter(node.get());
                codeBlock.setInfo("hidden-groovy");
            }
        } else if (codeType.matches("^sql\\(([^)]+)\\)$")) {
            renderSqlResult(codeBlock, markdownFile, codeType);
        } else if (codeType.matches("^plantuml\\(([^)]*)\\)$") || codeType.matches("^plantuml$")) {
            renderPlantUmlResult(codeBlock);
        } else {
            log.debug("unhandled code type: {}, delegating to default handler", codeType);
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
        var dataSourceConfig = dataSourceConfigResolver.apply(configMap.get("datasource"));
        if (dataSourceConfig == null) {
            newNodeToInsert = new Text("Error: DataSource '" + configMap.get("datasource") + "' not defined in config.");
        } else {
            try {
                newNodeToInsert = processSqlCodeBlock(sql, dataSourceConfig, markdownFile, maxRows);
            } catch (Exception e) {
                log.error("Error rendering SQL result", e);
                newNodeToInsert = new Text("Error rendering SQL result: " + e.getMessage()
                        +" original SQL: " + sql);
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
                parameterValues.put(param, "placeholder_value");
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
