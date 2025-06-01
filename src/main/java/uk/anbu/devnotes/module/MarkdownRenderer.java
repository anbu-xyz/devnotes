package uk.anbu.devnotes.module;

import lombok.extern.slf4j.Slf4j;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.image.attributes.ImageAttributesExtension;
import org.commonmark.ext.ins.InsExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.AttributeProvider;
import org.commonmark.renderer.html.HtmlRenderer;
import uk.anbu.devnotes.module.sql.SqlExecutor;
import uk.anbu.devnotes.service.ConfigService;
import uk.anbu.devnotes.types.Markdown;
import uk.anbu.devnotes.types.MarkdownFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class MarkdownRenderer {

    private final Function<SqlExecutor.JsonGenerationRequest, Path> sqlToJsonFileResolver;
    private final Function<SqlExecutor.HtmlTableRequest, String> sqlToHtmlTableResolver;
    private final Function<GroovyExecutor.GroovyCodeBlockRequest, Node> groovyCodeBlockResolver;
    private final Function<String, ConfigService.DataSourceConfig> dataSourceConfigResolver;

    public MarkdownRenderer(Function<SqlExecutor.JsonGenerationRequest, Path> sqlToJsonFileResolver,
                            Function<SqlExecutor.HtmlTableRequest, String> sqlToHtmlTableResolver,
                            Function<GroovyExecutor.GroovyCodeBlockRequest, Node> groovyCodeBlockResolver,
                            Function<String, ConfigService.DataSourceConfig> dataSourceConfigResolver) {
        this.sqlToJsonFileResolver = sqlToJsonFileResolver;
        this.sqlToHtmlTableResolver = sqlToHtmlTableResolver;
        this.groovyCodeBlockResolver = groovyCodeBlockResolver;
        this.dataSourceConfigResolver = dataSourceConfigResolver;
    }

    public String convertMarkdown(Markdown markdown, MarkdownFile markdownFile) {
        Integer codeBlockCounter = 0;
        List<Extension> extensions = List.of(TablesExtension.create(),
                StrikethroughExtension.create(),
                ImageAttributesExtension.create(),
                TaskListItemsExtension.create(),
                InsExtension.create());
        Parser parser = Parser.builder()
                .extensions(extensions)
                .build();
        Node document = parser.parse(markdown.text());
        processDocument(document, markdownFile, codeBlockCounter);
        HtmlRenderer renderer = HtmlRenderer.builder()
                .extensions(extensions)
                .attributeProviderFactory(context -> new ImageAttributeProvider())
                .build();
        return renderer.render(document);
    }

    private void processDocument(Node node, MarkdownFile markdownFile, Integer codeBlockCounter) {
        // Traverse the node tree
        log.trace("Rendering type: {}", node);
        if (node instanceof Link link) {
            String destination = link.getDestination();
            if (destination.startsWith("http:") || destination.startsWith("https:")) {
                log.trace("Link destination {} starts with http", destination);
            } else {
                link.setDestination("?filename=" + URLEncoder.encode(link.getDestination(), StandardCharsets.UTF_8));
            }
        } else if (node instanceof Image image) {
            String fileLocation = markdownFile.fileName()
                    .replaceAll("\\\\", "/") // Windows
                    .replaceFirst("/[^/]+$", ""); // Remove filename
            if (((Image) node).getDestination().startsWith("/plantumlContent?")) {
                // do nothing - this is to allow the plantuml renderer to render the image
            } else if (fileLocation.isEmpty() || fileLocation.equals(markdownFile.fileName())) { // If the file is in the root directory
                image.setDestination("/image?filename=" + URLEncoder.encode(image.getDestination(), StandardCharsets.UTF_8));
            } else if (image.getDestination().startsWith("/")) {
                image.setDestination("/image?filename=" + URLEncoder.encode(image.getDestination(), StandardCharsets.UTF_8));
            } else {
                image.setDestination("/image?filename=" + fileLocation + "/" + URLEncoder.encode(image.getDestination(), StandardCharsets.UTF_8));
            }

        } else if (node instanceof FencedCodeBlock) {
            codeBlockCounter++;
            processFencedCodeBlock((FencedCodeBlock) node, markdownFile, codeBlockCounter);
        }

        // Process siblings
        if (node.getNext() != null) {
            processDocument(node.getNext(), markdownFile, codeBlockCounter);
        }
        // Process children
        if (node.getFirstChild() != null) {
            processDocument(node.getFirstChild(), markdownFile, codeBlockCounter);
        }
    }

    private void processFencedCodeBlock(FencedCodeBlock codeBlock, MarkdownFile markdownFile, Integer codeBlockCounter) {
        String codeType = codeBlock.getInfo();
        // match codeType of format "groovy:targetType(config1:value1,config2:value2) or "groovy:targetType"
        if (codeType.matches("^groovy:([^(]+)\\(.*\\)$") || codeType.matches("^groovy:([^(]+)$")) {
            renderGroovyResult(codeBlock, markdownFile, codeType);
        } else if (codeType.matches("^sql\\(([^)]+)\\)$")) {
            renderSqlResult(codeBlock, markdownFile, codeType, codeBlockCounter);
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
        var groovyCodeBlockRequest = new GroovyExecutor.GroovyCodeBlockRequest(groovyScript, targetType,
                markdownFile, GroovyExecutor.GroovyCodeBlockConfig.fromMap(configMap));
        var node = groovyCodeBlockResolver.apply(groovyCodeBlockRequest);
        codeBlock.insertAfter(node);
        codeBlock.setInfo("hidden-groovy");
    }

    private void renderSqlResult(FencedCodeBlock codeBlock, MarkdownFile markdownFile, String codeType,
                                 Integer codeBlockCounter) {
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
            node = processSqlCodeBlock(sql, dataSourceConfig, markdownFile, maxRows, codeBlockCounter);
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
                                     MarkdownFile markdownFile, int maxRows,
                                     Integer codeBlockCounter) {

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
                markdownFile, codeBlockCounter);
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
                                      String dataSourceName, MarkdownFile markdownFile, Integer codeBlockCounter) {
        var request = new SqlExecutor.HtmlTableRequest(sqlText, outputPath, parameterValues, dataSourceName,
                markdownFile, codeBlockCounter);
        String tableString = sqlToHtmlTableResolver.apply(request);
        HtmlBlock htmlBlock = new HtmlBlock();
        htmlBlock.setLiteral(tableString);
        return htmlBlock;
    }

    public static String generateOutputFileName(MarkdownFile markdownFile, String scriptText) {
        String hash = generateHash(scriptText);
        var fileName = Paths.get(markdownFile.fileName()).getFileName().toString();
        var generatedFileName = fileName.replaceFirst("[.][^.]+$", "") + "." + hash + ".output";
        return markdownFile.fullPath().getParent().resolve(generatedFileName).toString();
    }

    private static String generateHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.substring(0, 16); // Use first 16 characters of the hash
        } catch (NoSuchAlgorithmException e) {
            log.error("Error generating hash", e);
            return "error";
        }
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
