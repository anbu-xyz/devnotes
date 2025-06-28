package uk.anbu.devnotes.module;

import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import uk.anbu.devnotes.util.GroovyShellRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import static uk.anbu.devnotes.util.FileBasedCache.readFromFile;
import static uk.anbu.devnotes.util.FileBasedCache.saveOutput;


@Slf4j
public class GroovyRenderer {

    private final Supplier<Optional<String>> chromeDriverLocationSupplier;

    public GroovyRenderer(Supplier<Optional<String>> chromeDriverLocationSupplier) {
        this.chromeDriverLocationSupplier = chromeDriverLocationSupplier;
    }

    private GroovyOutput processGroovyCodeBlock(GroovyCodeBlockRequest request) {
        setEnvVariables();
        var outputString = GroovyShellRunner.execute(request.groovyScript);
        var node = convertOutputToNode(request.targetType, outputString);
        return new GroovyOutput(outputString, node);
    }

    public Optional<Node> renderResult(FencedCodeBlock codeBlock, String cacheFileName, String codeType) {
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
                return Optional.empty();
            }
            String[] configParts = codeType.substring(openParenIndex + 1, closeParenIndex).split(",");
            for (String configKeyValue : configParts) {
                String[] keyValue = configKeyValue.split(":");
                configMap.put(keyValue[0], keyValue[1]);
            }
        }

        String groovyScriptText = codeBlock.getLiteral();

        String targetType = codeType.substring("groovy:".length(), openParenIndex);
        var groovyCodeBlockRequest = new GroovyRenderer.GroovyCodeBlockRequest(groovyScriptText, targetType,
                GroovyRenderer.GroovyCodeBlockConfig.fromMap(configMap));

        Path outputFile = Paths.get(cacheFileName);

        Node node;
        if (groovyCodeBlockRequest.config().cachingEnabled()) {
            if (Files.exists(outputFile)) {
                var outputString = readFromFile(outputFile, cacheFileName);
                node = GroovyRenderer.convertOutputToNode(targetType, outputString);
            } else {
                var output = processGroovyCodeBlock(groovyCodeBlockRequest);
                saveOutput(outputFile, output.outputString());
                node = output.node();
            }
        } else {
            var output = processGroovyCodeBlock(groovyCodeBlockRequest);
            node = output.node();
        }
        return Optional.of(node);
    }

    public static Node convertOutputToNode(String targetType, String output) {
        if ("html".equals(targetType)) {
            HtmlBlock htmlBlock = new HtmlBlock();
            htmlBlock.setLiteral(output);
            return htmlBlock;
        } else if ("text".equals(targetType)) {
            return new Text(output);
        } else if ("code-block".equals(targetType)) {
            FencedCodeBlock fencedCodeBlock = new FencedCodeBlock();
            fencedCodeBlock.setInfo("text");
            fencedCodeBlock.setLiteral(output);
            return fencedCodeBlock;
        } else if ("csv-table".equals(targetType) || "csv-table-with-header".equals(targetType)) {
            return csvToHtmlTable(targetType, output);
        } else {
            return new Text(String.format("Error: Unknown target type '%s', use target type 'html', 'text', " +
                    "'code-block', 'csv-table' or 'csv-table-with-header'", targetType));
        }
    }

    private void setEnvVariables() {
        if (chromeDriverLocationSupplier.get().isPresent()) {
            log.info("Setting chrome driver location to {}", chromeDriverLocationSupplier.get().get());
            System.setProperty("webdriver.chrome.driver", Objects.requireNonNull(chromeDriverLocationSupplier.get().orElse(null)));
        } else {
            log.info("Chrome driver location not set");
        }
    }

    private static Node csvToHtmlTable(String targetType, String output) {
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
        return htmlBlock;
    }

    public record GroovyCodeBlockConfig(Boolean cachingEnabled) {
        // constructor to parse from Map<String, String>
        public static GroovyCodeBlockConfig fromMap(Map<String, String> configMap) {
            var cachingEnabled = Boolean.parseBoolean(configMap.getOrDefault("cacheEnabled", "true"));
            return new GroovyCodeBlockConfig(cachingEnabled);
        }
    }

    public record GroovyCodeBlockRequest(String groovyScript, String targetType, GroovyCodeBlockConfig config) {}

    public record GroovyOutput(String outputString, Node node) {}
}
