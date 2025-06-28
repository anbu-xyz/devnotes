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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static uk.anbu.devnotes.module.GroovyRenderer.convertOutputToNode;
import static uk.anbu.devnotes.util.FileBasedCache.generateOutputFileName;
import static uk.anbu.devnotes.util.FileBasedCache.readFromFile;
import static uk.anbu.devnotes.util.FileBasedCache.saveOutput;

@Slf4j
@Builder
public class CodeBlockTransformer {

    @Builder.Default
    private Integer codeBlockCounter = 0;
    private final MarkdownFile markdownFile;
    private final Function<SqlExecutor.JsonGenerationRequest, Path> sqlToJsonFileResolver;
    private final Function<SqlExecutor.HtmlTableRequest, String> sqlToHtmlTableResolver;
    private final Function<GroovyRenderer.GroovyCodeBlockRequest, GroovyRenderer.GroovyOutput> groovyCodeBlockResolver;
    private final Function<String, ConfigService.DataSourceConfig> dataSourceConfigResolver;

    public void transform(Node current) {
        if (current instanceof FencedCodeBlock) {
            processFencedCodeBlock((FencedCodeBlock) current);
        }
        if (current.getNext() != null) {
            transform(current.getNext());
        }
        if (current.getFirstChild() != null) {
            transform(current.getFirstChild());
        }
    }

    private void processFencedCodeBlock(FencedCodeBlock codeBlock) {
        String codeType = codeBlock.getInfo();
        // match codeType of format "groovy:targetType(config1:value1,config2:value2) or "groovy:targetType"
        if (codeType.matches("^groovy:([^(]+)\\(.*\\)$") || codeType.matches("^groovy:([^(]+)$")) {
            renderGroovyResult(codeBlock, markdownFile, codeType);
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

    private void renderGroovyResult(FencedCodeBlock codeBlock, MarkdownFile markdownFile, String codeType) {
        // Assuming codeType string is of format "groovy:targetType(config1:value1,config2:value2)"
        // find location of first opening parenthesis
        int openParenIndex = codeType.indexOf('(');
        Map<String, String> configMap = new HashMap<>();
        if (openParenIndex == -1) {
            log.debug("Open parenthesis not found in config string {}, using default config", codeType);
            openParenIndex = codeType.length();
        } else {
            // find location of last closing parenthesis
            int closeParenIndex = codeType.lastIndexOf(')');
            if (closeParenIndex == -1) {
                log.error("Error rendering Groovy result: missing closing parenthesis. Unable to read config from {}", codeType);
                return;
            }
            String[] configParts = codeType.substring(openParenIndex + 1, closeParenIndex).split(",");
            for (String configKeyValue : configParts) {
                String[] keyValue = configKeyValue.split(":");
                configMap.put(keyValue[0], keyValue[1]);
            }
        }

        String groovyScript = codeBlock.getLiteral();

        String targetType = codeType.substring("groovy:".length(), openParenIndex);
        var groovyCodeBlockRequest = new GroovyRenderer.GroovyCodeBlockRequest(groovyScript, targetType,
                GroovyRenderer.GroovyCodeBlockConfig.fromMap(configMap));

        String outputFileName = generateOutputFileName(markdownFile, groovyScript);
        Path outputPath = Paths.get(outputFileName);

        Node node;
        if (groovyCodeBlockRequest.config().cachingEnabled()) {
            if (Files.exists(outputPath)) {
                String output = readFromFile(outputPath, outputFileName);
                node = convertOutputToNode(targetType, output);
            } else {
                var output = groovyCodeBlockResolver.apply(groovyCodeBlockRequest);
                saveOutput(outputPath, output.outputString());
                node = output.node();
            }
        } else {
            var output = groovyCodeBlockResolver.apply(groovyCodeBlockRequest);
            node = output.node();
        }
        codeBlock.insertAfter(node);
        codeBlock.setInfo("hidden-groovy");
    }

    private void renderSqlResult(FencedCodeBlock codeBlock, MarkdownFile markdownFile, String codeType) {
        String configString = codeType.substring(4, codeType.length() - 1);
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
        Node node;
        var maxRows = readMaxRows(configMap);
        var dataSourceConfig = dataSourceConfigResolver.apply(configMap.get("datasource"));
        if (dataSourceConfig == null) {
            node = new Text("Error: DataSource '" + configMap.get("datasource") + "' not defined in config.");
        } else {
            node = processSqlCodeBlock(sql, dataSourceConfig, markdownFile, maxRows);
        }
        codeBlock.insertAfter(node);
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

    private Node processSqlCodeBlock(String sql, ConfigService.DataSourceConfig dataSourceConfig,
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
        var outputPath = sqlToJsonFileResolver.apply(request);

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

    private Node renderSqlResultTable(String sqlText, Path outputPath, Map<String, String> parameterValues,
                                      String dataSourceName, MarkdownFile markdownFile) {
        var request = new SqlExecutor.HtmlTableRequest(sqlText, outputPath, parameterValues, dataSourceName,
                markdownFile, codeBlockCounter);
        codeBlockCounter++;
        String tableString = sqlToHtmlTableResolver.apply(request);
        HtmlBlock htmlBlock = new HtmlBlock();
        htmlBlock.setLiteral(tableString);
        return htmlBlock;
    }

}
