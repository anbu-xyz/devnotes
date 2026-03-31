package uk.anbu.devnotes.module;

import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import uk.anbu.devnotes.util.GroovyShellRunner;

import java.io.IOException;
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

    private GroovyOutput processGroovyCodeBlock(GroovyCodeBlockRequest request) throws Exception {
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
                node = convertOutputToNode(targetType, outputString);
            } else {
                try {
                    var output = processGroovyCodeBlock(groovyCodeBlockRequest);
                    saveOutput(outputFile, output.outputString());
                    node = output.node();
                } catch (Exception e) {
                    log.error("Error rendering Groovy result with caching enabled", e);
                    node = new Text("Error executing Groovy script: " + e.getMessage());
                }
            }
        } else {
            try {
                var output = processGroovyCodeBlock(groovyCodeBlockRequest);
                node = output.node();
            } catch (Exception e) {
                log.error("Error rendering Groovy result with caching disabled", e);
                node = new Text("Error executing Groovy script: " + e.getMessage());
            }
        }
        return Optional.of(node);
    }

    /**
     * Renders the groovy block and wraps the output in a {@code <div class="groovy-block">}
     * container that carries a {@code data-groovy-id} attribute for client-side refresh.
     */
    public Optional<Node> renderResultWrapped(FencedCodeBlock codeBlock, String cacheFileName,
                                              String codeType, String groovyId) {
        return renderResult(codeBlock, cacheFileName, codeType)
                .map(node -> wrapInGroovyBlock(node, groovyId));
    }

    /**
     * Forces re-execution by deleting any existing cache file, then delegates to
     * {@link #renderResultWrapped}.
     */
    public Optional<Node> renderResultFresh(FencedCodeBlock codeBlock, String cacheFileName,
                                            String codeType, String groovyId) {
        Path outputFile = Paths.get(cacheFileName);
        try {
            Files.deleteIfExists(outputFile);
        } catch (IOException e) {
            log.warn("Could not delete Groovy cache file {}", cacheFileName, e);
        }
        return renderResultWrapped(codeBlock, cacheFileName, codeType, groovyId);
    }

    private static HtmlBlock wrapInGroovyBlock(Node node, String groovyId) {
        var innerHtml = convertNodeToHtml(node);
        var wrapper = new HtmlBlock();
        wrapper.setLiteral("<div class=\"groovy-block\" data-groovy-id=\"" + groovyId + "\">"
                + innerHtml + "</div>\n");
        return wrapper;
    }

    private static String convertNodeToHtml(Node node) {
        if (node instanceof HtmlBlock htmlBlock) {
            return htmlBlock.getLiteral();
        } else if (node instanceof Text text) {
            return "<pre class=\"groovy-text-output\">" + escapeHtml(text.getLiteral()) + "</pre>";
        } else if (node instanceof FencedCodeBlock fcb) {
            var lang = fcb.getInfo() != null ? fcb.getInfo() : "text";
            return "<pre><code class=\"language-" + lang + "\">"
                    + escapeHtml(fcb.getLiteral()) + "</code></pre>";
        }
        return "<pre>" + escapeHtml(node.toString()) + "</pre>";
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private Node convertOutputToNode(String targetType, String output) {
        switch (targetType) {
            case "html" -> {
                HtmlBlock htmlBlock = new HtmlBlock();
                htmlBlock.setLiteral(output);
                return htmlBlock;
            }
            case "text" -> {
                return new Text(output);
            }
            case "code-block" -> {
                FencedCodeBlock fencedCodeBlock = new FencedCodeBlock();
                fencedCodeBlock.setInfo("text");
                fencedCodeBlock.setLiteral(output);
                return fencedCodeBlock;
            }
            case "csv-table", "csv-table-with-header" -> {
                return csvToHtmlTable(targetType, output);
            }
            case null, default -> {
                return new Text(String.format("Error: Unknown target type '%s', use target type 'html', 'text', " +
                        "'code-block', 'csv-table' or 'csv-table-with-header'", targetType));
            }
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

    private record GroovyCodeBlockConfig(Boolean cachingEnabled) {
        public static GroovyCodeBlockConfig fromMap(Map<String, String> configMap) {
            var cachingEnabled = Boolean.parseBoolean(configMap.getOrDefault("cacheEnabled", "true"));
            return new GroovyCodeBlockConfig(cachingEnabled);
        }
    }

    private record GroovyCodeBlockRequest(String groovyScript, String targetType, GroovyCodeBlockConfig config) {}

    private record GroovyOutput(String outputString, Node node) {}
}
