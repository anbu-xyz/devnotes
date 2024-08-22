package uk.anbu.devnotes.module;

import lombok.extern.slf4j.Slf4j;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.AttributeProvider;
import org.commonmark.renderer.html.HtmlRenderer;
import uk.anbu.devnotes.service.DataSourceConfig;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
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
    private final Function<String, DataSourceConfig> dataSourceConfigResolver;

    public MarkdownRenderer(Function<SqlExecutor.JsonGenerationRequest, Path> sqlToJsonFileResolver,
                            Function<SqlExecutor.HtmlTableRequest, String> sqlToHtmlTableResolver,
                            Function<GroovyExecutor.GroovyCodeBlockRequest, Node> groovyCodeBlockResolver,
                            Function<String, DataSourceConfig> dataSourceConfigResolver) {
        this.sqlToJsonFileResolver = sqlToJsonFileResolver;
        this.sqlToHtmlTableResolver = sqlToHtmlTableResolver;
        this.groovyCodeBlockResolver = groovyCodeBlockResolver;
        this.dataSourceConfigResolver = dataSourceConfigResolver;
    }


    public String convertMarkdown(String markdown, String fileNameWithRelativePath) {
        List<Extension> extensions = List.of(TablesExtension.create());
        Parser parser = Parser.builder()
                .extensions(extensions)
                .build();
        Node document = parser.parse(markdown);
        processDocument(document, fileNameWithRelativePath);
        HtmlRenderer renderer = HtmlRenderer.builder()
                .extensions(extensions)
                .attributeProviderFactory(context -> new ImageAttributeProvider())
                .build();
        return renderer.render(document);
    }

    private void processDocument(Node node, String fileNameWithRelativePath) {
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
            String fileLocation = fileNameWithRelativePath
                    .replaceAll("\\\\", "/") // Windows
                    .replaceFirst("/[^/]+$", ""); // Remove filename
            if (fileLocation.isEmpty() || fileLocation.equals(fileNameWithRelativePath)) { // If the file is in the root directory
                fileLocation = ".";
            }

            image.setDestination("/image?filename=" + fileLocation + "/" + URLEncoder.encode(image.getDestination(), StandardCharsets.UTF_8));
        }

        if (node instanceof FencedCodeBlock) {
            processFencedCodeBlock((FencedCodeBlock) node, fileNameWithRelativePath, dataSourceConfigResolver);
        }

        // Process siblings
        if (node.getNext() != null) {
            processDocument(node.getNext(), fileNameWithRelativePath);
        }
        // Process children
        if (node.getFirstChild() != null) {
            processDocument(node.getFirstChild(), fileNameWithRelativePath);
        }
    }

    private void processFencedCodeBlock(FencedCodeBlock codeBlock, String fileNameWithRelativePath, 
                                        Function<String, DataSourceConfig> dataSourceConfigResolver) {
        String codeType = codeBlock.getInfo();
        if (codeType.matches("^groovy:([^:]+)$")) {
            renderGroovyResult(codeBlock, fileNameWithRelativePath, codeType);
        } else if (codeType.matches("^sql\\(([^)]+)\\)$")) {
            renderSqlResult(codeBlock, fileNameWithRelativePath, dataSourceConfigResolver, codeType);
        }
    }

    private void renderGroovyResult(FencedCodeBlock codeBlock, String fileNameWithRelativePath, String codeType) {
        String targetType = codeType.substring(7);
        String groovyScript = codeBlock.getLiteral();
        var groovyCodeBlockRequest = new GroovyExecutor.GroovyCodeBlockRequest(groovyScript, targetType,
                fileNameWithRelativePath);
        var node = groovyCodeBlockResolver.apply(groovyCodeBlockRequest);
        codeBlock.insertAfter(node);
        codeBlock.setInfo("hidden-groovy");
    }

    private void renderSqlResult(FencedCodeBlock codeBlock, String fileNameWithRelativePath, 
                                 Function<String, DataSourceConfig> dataSourceConfigResolver, String codeType) {
        String dataSourceName = codeType.substring(4, codeType.length() - 1);
        String sql = codeBlock.getLiteral();
        Node node;
        var dataSourceConfig = dataSourceConfigResolver.apply(dataSourceName);
        if (dataSourceConfig == null) {
            node = new Text("Error: DataSource '" + dataSourceName + "' not defined in config.");
        } else {
            node = processSqlCodeBlock(sql, dataSourceConfig, fileNameWithRelativePath);
        }
        codeBlock.insertAfter(node);
        codeBlock.setInfo("hidden-sql");
    }

    private Node processSqlCodeBlock(String sql, DataSourceConfig dataSourceConfig, String fileNameWithRelativePath) {

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
                fileNameWithRelativePath);
        var outputPath = sqlToJsonFileResolver.apply(request);

        return renderSqlResultTable(sql, outputPath, parameterValues, dataSourceConfig.name(),
                fileNameWithRelativePath);
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
                                      String dataSourceName, String markdownFileName) {
        var request = new SqlExecutor.HtmlTableRequest(sqlText, outputPath, parameterValues, dataSourceName, markdownFileName);
        String tableString = sqlToHtmlTableResolver.apply(request);
        HtmlBlock htmlBlock = new HtmlBlock();
        htmlBlock.setLiteral(tableString);
        return htmlBlock;
    }

    public static String generateOutputFileName(String docsDirectory, String markdownFileName, String scriptText) {
        String hash = generateHash(scriptText);
        return Paths.get(docsDirectory, markdownFileName).getParent().resolve(
                Paths.get(markdownFileName).getFileName().toString().replaceFirst("[.][^.]+$", "") + "." + hash + ".output"
        ).toString();
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
