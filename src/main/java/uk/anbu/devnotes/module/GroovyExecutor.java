package uk.anbu.devnotes.module;

import groovy.lang.GroovyShell;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.springframework.stereotype.Component;
import uk.anbu.devnotes.types.MarkdownFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;

import static uk.anbu.devnotes.module.MarkdownRenderer.generateOutputFileName;

@Slf4j
@RequiredArgsConstructor
@Component
public class GroovyExecutor {

    public Node processGroovyCodeBlock(GroovyCodeBlockRequest request) {
        String outputFileName = generateOutputFileName(request.markdownFile(), request.groovyScript);
        Path outputPath = Paths.get(outputFileName);

        String output;
        if (request.config().cachingEnabled()) {
            if (Files.exists(outputPath)) {
                output = readFromFile(outputPath, outputFileName);
            } else {
                output = executeGroovyScript(request.groovyScript);
                saveOutput(outputFileName, output);
            }
        } else {
            output = executeGroovyScript(request.groovyScript);
        }

        // Replace the code block with the output
        if ("html".equals(request.targetType)) {
            HtmlBlock htmlBlock = new HtmlBlock();
            htmlBlock.setLiteral(output);
            return htmlBlock;
        } else if ("text".equals(request.targetType)) {
            return new Text(output);
        } else if ("code-block".equals(request.targetType)) {
            FencedCodeBlock fencedCodeBlock = new FencedCodeBlock();
            fencedCodeBlock.setInfo("text");
            fencedCodeBlock.setLiteral(output);
            return fencedCodeBlock;
        } else if ("csv-table".equals(request.targetType) || "csv-table-with-header".equals(request.targetType)) {
            return csvToHtmlTable(request.targetType, output);
        } else {
            return new Text(String.format("Error: Unknown target type '%s', use target type 'html', 'text', " +
                    "'code-block', 'csv-table' or 'csv-table-with-header'", request.targetType));
        }
    }

    private static String readFromFile(Path outputPath, String outputFileName) {
        String output;
        // If the output file already exists, read its content
        try {
            output = Files.readString(outputPath);
            log.info("Using existing output file: {}", outputFileName);
        } catch (IOException e) {
            log.error("Error reading existing output file: {}", outputFileName, e);
            output = "Error: Unable to read existing output file";
        }
        return output;
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

    private String executeGroovyScript(String script) {
        GroovyShell shell = new GroovyShell();
        try {
            log.info("Executing Groovy script");
            log.trace("Script source:\n{}", script);
            Object result = shell.evaluate(script);
            log.info("Finished executing Groovy script");
            log.trace("Script result:\n{}", result);
            return result != null ? result.toString() : "";
        } catch (Exception e) {
            log.error("Error executing Groovy script", e);
            return "Error: " + e.getMessage();
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

    public record GroovyCodeBlockConfig(Boolean cachingEnabled) {
        // constructor to parse from Map<String, String>
        public static GroovyCodeBlockConfig fromMap(Map<String, String> configMap) {
            var cachingEnabled = Boolean.parseBoolean(configMap.getOrDefault("cacheEnabled", "true"));
            return new GroovyCodeBlockConfig(cachingEnabled);
        }
    }

    public record GroovyCodeBlockRequest(String groovyScript, String targetType, MarkdownFile markdownFile,
                                         GroovyCodeBlockConfig config) {}
}
