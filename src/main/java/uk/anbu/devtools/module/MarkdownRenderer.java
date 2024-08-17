package uk.anbu.devtools.module;

import com.fasterxml.jackson.databind.ObjectMapper;
import groovy.lang.GroovyShell;
import lombok.RequiredArgsConstructor;
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
import org.springframework.stereotype.Component;
import uk.anbu.devtools.service.ConfigService;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarkdownRenderer {

    private final ConfigService configService;
    private final ObjectMapper objectMapper;
    private final SqlExecutor sqlExecutor;

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
            processFencedCodeBlock((FencedCodeBlock) node, fileNameWithRelativePath);
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

    private void processFencedCodeBlock(FencedCodeBlock codeBlock, String fileNameWithRelativePath) {
        String codeType = codeBlock.getInfo();
        if (codeType.matches("^groovy:([^:]+)$")) {
            String targetType = codeType.substring(7);
            processGroovyCodeBlock(codeBlock, targetType, fileNameWithRelativePath);
        } else if (codeType.matches("^sql\\(([^)]+)\\)$")) {
            String dataSourceName = codeType.substring(4, codeType.length() - 1);
            String sql = codeBlock.getLiteral();
            var node = processSqlCodeBlock(sql, dataSourceName, fileNameWithRelativePath);
            codeBlock.insertAfter(node);
            codeBlock.unlink();
        }
    }

    private Node processSqlCodeBlock(String sql, String dataSourceName, String fileNameWithRelativePath) {
        ConfigService.DataSourceConfig dataSourceConfig = configService.getDataSourceConfig(dataSourceName);
        if (dataSourceConfig == null) {
            return new Text("Error: DataSource '" + dataSourceName + "' not found.");
        }

        List<String> parameterNames = extractParameterNames(sql);
        Map<Integer, String> parameterValues = new LinkedHashMap<>();

        if (!parameterNames.isEmpty()) {
            // TODO: Implement user input for parameter values
            // For now, we'll use placeholder values
            Integer i = 1;
            for (String param : parameterNames) {
                parameterValues.put(i++, "placeholder_value");
            }
        }

        String outputFileName = generateOutputFileName(fileNameWithRelativePath, sql + parameterValues.toString());
        Path outputPath = Paths.get(outputFileName);

        if (!Files.exists(outputPath)) {
            sqlExecutor.executeSqlAndSaveOutput(dataSourceConfig, sql, parameterValues, outputPath);
        }

        return renderSqlResultTable(sql, outputPath, parameterValues);
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

    private Node renderSqlResultTable(String sqlText, Path outputPath, Map<Integer, String> parameterValues) {
        String tableString =  sqlExecutor.convertToHtmlTable(sqlText, outputPath, parameterValues);
        HtmlBlock htmlBlock = new HtmlBlock();
        htmlBlock.setLiteral(tableString);
        return htmlBlock;
    }

    private void processGroovyCodeBlock(FencedCodeBlock codeBlock, String targetType, String fileNameWithRelativePath) {
        String groovyScript = codeBlock.getLiteral();
        String outputFileName = generateOutputFileName(fileNameWithRelativePath, groovyScript);
        Path outputPath = Paths.get(outputFileName);

        String output;
        if (Files.exists(outputPath)) {
            // If the output file already exists, read its content
            try {
                output = Files.readString(outputPath);
                log.info("Using existing output file: {}", outputFileName);
            } catch (IOException e) {
                log.error("Error reading existing output file: {}", outputFileName, e);
                output = "Error: Unable to read existing output file";
            }
        } else {
            // If the output file doesn't exist, execute the Groovy script and save the output
            output = executeGroovyScript(groovyScript);
            saveOutput(outputFileName, output);
        }

        // Replace the code block with the output
        if ("html".equals(targetType)) {
            HtmlBlock htmlBlock = new HtmlBlock();
            htmlBlock.setLiteral(output);
            codeBlock.insertAfter(htmlBlock);
        } else if ("text".equals(targetType)) {
            Text text = new Text(output);
            codeBlock.insertAfter(text);
        } else if ("code-block".equals(targetType)) {
            FencedCodeBlock fencedCodeBlock = new FencedCodeBlock();
            fencedCodeBlock.setInfo("text");
            fencedCodeBlock.setLiteral(output);
            codeBlock.insertAfter(fencedCodeBlock);
        } else if("csv-table".equals(targetType) || "csv-table-with-header".equals(targetType)) {
            csvToHtmlTable(codeBlock, targetType, output);
        }

        codeBlock.unlink();
    }

    private static void csvToHtmlTable(FencedCodeBlock codeBlock, String targetType, String output) {
        String[] lines = output.split("\n");
        StringBuilder tableHtml = new StringBuilder();
        tableHtml.append("<table>");
        if ("csv-table-with-header".equals(targetType)) {
            String[] header = lines[0].split(",");
            tableHtml.append("<tr>");
            for (String headerCell : header) {
                tableHtml.append("<th>").append(headerCell).append("</th>");
            }
            tableHtml.append("</tr>");
            lines = Arrays.copyOfRange(lines, 1, lines.length);
        }
        for (String line : lines) {
            String[] cells = line.split(",");
            tableHtml.append("<tr>");
            for (String cell : cells) {
                tableHtml.append("<td>").append(cell).append("</td>");
            }
            tableHtml.append("</tr>");
        }
        tableHtml.append("</table>");

        HtmlBlock htmlBlock = new HtmlBlock();
        htmlBlock.setLiteral(tableHtml.toString());
        codeBlock.insertAfter(htmlBlock);
    }

    private String executeGroovyScript(String script) {
        GroovyShell shell = new GroovyShell();
        try {
            Object result = shell.evaluate(script);
            return result != null ? result.toString() : "";
        } catch (Exception e) {
            log.error("Error executing Groovy script", e);
            return "Error: " + e.getMessage();
        }
    }

    private String generateOutputFileName(String markdownFileName, String groovyScript) {
        String hash = generateHash(groovyScript);
        return Paths.get(configService.getDocsDirectory(), markdownFileName).getParent().resolve(
                Paths.get(markdownFileName).getFileName().toString().replaceFirst("[.][^.]+$", "") + "." + hash + ".output"
        ).toString();
    }

    private String generateHash(String input) {
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

    private void saveOutput(String fileName, String content) {
        try {
            Path outputPath = Paths.get(fileName);
            Files.createDirectories(outputPath.getParent());
            Files.write(outputPath, content.getBytes());
        } catch (IOException e) {
            log.error("Error saving output file", e);
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
