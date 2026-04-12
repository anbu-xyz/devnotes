package uk.anbu.devnotes.module;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import uk.anbu.devnotes.markdown.code.groovyblock.GroovyCodeblockConfig;
import uk.anbu.devnotes.util.GroovyShellRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
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

    public Optional<Node> renderResult(FencedCodeBlock codeBlock, String cacheFileName) {
        return renderResultFromParsed(parseYamlConfig(codeBlock.getLiteral()), cacheFileName);
    }

    /**
     * Renders the groovy block and wraps the output in a {@code <div class="groovy-block">}
     * container that carries a {@code data-groovy-id} attribute for client-side refresh.
     * When the config has {@code controls-enabled: false} the wrapper also gets
     * {@code data-groovy-controls="false"}, which tells the JS not to inject the ⋮ menu.
     */
    public Optional<Node> renderResultWrapped(FencedCodeBlock codeBlock, String cacheFileName,
                                              String groovyId) {
        var parsed = parseYamlConfig(codeBlock.getLiteral());
        return renderResultFromParsed(parsed, cacheFileName)
                .map(node -> wrapInGroovyBlock(node, groovyId, parsed.config().controlsEnabled()));
    }

    /**
     * Executes a Groovy script with the given target type and returns the rendered HTML string.
     * Any execution error is embedded in a {@code <pre>} block rather than thrown.
     */
    public String executeToHtml(String groovyScript, String targetType) {
        try {
            setEnvVariables();
            var outputString = GroovyShellRunner.execute(groovyScript);
            var node = convertOutputToNode(targetType, outputString);
            return convertNodeToHtml(node);
        } catch (Exception e) {
            log.error("Error executing Groovy script in playground", e);
            return "<pre class=\"groovy-text-output\">" + escapeHtml(e.getMessage()) + "</pre>";
        }
    }

    /**
     * Forces re-execution by deleting any existing cache file, then delegates to
     * {@link #renderResultWrapped}.
     */
    public Optional<Node> renderResultFresh(FencedCodeBlock codeBlock, String cacheFileName,
                                            String groovyId) {
        Path outputFile = Paths.get(cacheFileName);
        try {
            Files.deleteIfExists(outputFile);
        } catch (IOException e) {
            log.warn("Could not delete Groovy cache file {}", cacheFileName, e);
        }
        return renderResultWrapped(codeBlock, cacheFileName, groovyId);
    }

    // -------------------------------------------------------------------------
    // Config parsing
    // -------------------------------------------------------------------------

    /**
     * Parses the block configuration from the YAML header at the top of the block body.
     *
     * <p>The body must contain a {@code ---} separator on its own line; everything before it
     * is parsed as YAML config and everything after is the Groovy script.  If no separator is
     * found the entire body is treated as the script with default config.
     */
    static ParsedConfig parseYamlConfig(String literal) {
        var separator = "\n---\n";
        int sepIdx = literal.indexOf(separator);
        String yamlPart;
        String scriptPart;
        if (sepIdx >= 0) {
            yamlPart = literal.substring(0, sepIdx).trim();
            scriptPart = literal.substring(sepIdx + separator.length());
        } else {
            // No separator - treat the entire body as script with defaults
            return new ParsedConfig("html", literal, new GroovyCodeBlockConfig(true, true));
        }

        if (yamlPart.isBlank()) {
            return new ParsedConfig("html", scriptPart, new GroovyCodeBlockConfig(true, true));
        }

        try {
            var mapper = new ObjectMapper(new YAMLFactory());
            var yamlCfg = mapper.readValue(yamlPart, GroovyCodeblockConfig.class);
            var output = yamlCfg.getOutput() != null ? yamlCfg.getOutput() : "html";
            var config = new GroovyCodeBlockConfig(yamlCfg.isCacheEnabled(), yamlCfg.isControlsEnabled());
            return new ParsedConfig(output, scriptPart, config);
        } catch (Exception e) {
            log.warn("Failed to parse YAML header in groovy block: {}", e.getMessage());
            return new ParsedConfig("html", scriptPart, new GroovyCodeBlockConfig(true, true));
        }
    }


    // -------------------------------------------------------------------------
    // Core rendering
    // -------------------------------------------------------------------------

    private Optional<Node> renderResultFromParsed(ParsedConfig parsed, String cacheFileName) {
        var request = new GroovyCodeBlockRequest(parsed.script(), parsed.targetType(), parsed.config());
        var outputFile = Paths.get(cacheFileName);

        Node node;
        if (request.config().cachingEnabled()) {
            if (Files.exists(outputFile)) {
                var outputString = readFromFile(outputFile, cacheFileName);
                node = convertOutputToNode(parsed.targetType(), outputString);
            } else {
                try {
                    var output = processGroovyCodeBlock(request);
                    saveOutput(outputFile, output.outputString());
                    node = output.node();
                } catch (Exception e) {
                    log.error("Error rendering Groovy result with caching enabled", e);
                    node = new Text("Error executing Groovy script: " + e.getMessage());
                }
            }
        } else {
            try {
                var output = processGroovyCodeBlock(request);
                node = output.node();
            } catch (Exception e) {
                log.error("Error rendering Groovy result with caching disabled", e);
                node = new Text("Error executing Groovy script: " + e.getMessage());
            }
        }
        return Optional.of(node);
    }

    private static HtmlBlock wrapInGroovyBlock(Node node, String groovyId, boolean showControls) {
        var innerHtml = convertNodeToHtml(node);
        var wrapper = new HtmlBlock();
        var controlsAttr = showControls ? "" : " data-groovy-controls=\"false\"";
        wrapper.setLiteral("<div class=\"groovy-block\" data-groovy-id=\"" + groovyId + "\"" + controlsAttr + ">"
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

    // -------------------------------------------------------------------------
    // Internal types
    // -------------------------------------------------------------------------

    /** Holds the fully-parsed configuration extracted from either format. */
    record ParsedConfig(String targetType, String script, GroovyCodeBlockConfig config) {}

    private record GroovyCodeBlockConfig(Boolean cachingEnabled, Boolean controlsEnabled) {}

    private record GroovyCodeBlockRequest(String groovyScript, String targetType, GroovyCodeBlockConfig config) {}

    private record GroovyOutput(String outputString, Node node) {}
}
