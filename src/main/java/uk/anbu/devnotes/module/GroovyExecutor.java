package uk.anbu.devnotes.module;

import groovy.lang.GroovyShell;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.springframework.stereotype.Component;
import uk.anbu.devnotes.service.ConfigService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static uk.anbu.devnotes.module.MarkdownRenderer.generateOutputFileName;

@Slf4j
@RequiredArgsConstructor
@Component
public class GroovyExecutor {
    private final ConfigService configService;

    public Node processGroovyCodeBlock(String groovyScript, String targetType, String fileNameWithRelativePath) {
        String docsDirectory = configService.getDocsDirectory();
        String outputFileName = generateOutputFileName(docsDirectory, fileNameWithRelativePath, groovyScript);
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
            Object result = shell.evaluate(script);
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

}
