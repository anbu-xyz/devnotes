package uk.anbu.devnotes.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devnotes.module.SqlExecutor;
import uk.anbu.devnotes.service.ConfigService;
import uk.anbu.devnotes.service.DataSourceConfig;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class SqlExecutionController {

    private final ConfigService configService;
    private final SqlExecutor sqlExecutor;
    private final ObjectMapper objectMapper;
    private final TemplateEngine templateEngine;

    @GetMapping("/reExecuteSql")
    public ResponseEntity<String> reExecuteSql() {
        TemplateOutput output = new StringOutput();
        var params = new HashMap<String, Object>();
        params.put("dataSources", configService.getDataSources().keySet());
        templateEngine.render("sql-executor.jte", params, output);

        return ResponseEntity.status(HttpStatus.OK)
                .body(output.toString());
    }

    @PostMapping(value = "/reExecuteSql", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> reExecuteSql(@RequestBody SqlExecutionRequest request) {
        try {
            String dataSourceName = request.getDatasourceName();
            DataSourceConfig dataSourceConfig = configService.getDataSourceConfig(dataSourceName);

            if (dataSourceConfig == null) {
                log.error("Invalid datasource name: {}", dataSourceName);
                return ResponseEntity.badRequest().body("Invalid datasource name:" + dataSourceName);
            }

            var jsonGenerationRequest = new SqlExecutor.JsonGenerationRequest(dataSourceConfig, request.getSql(),
                    request.getParameterValues(), request.getMarkdownFileName(), request.isForceExecute());
            var outputPath = sqlExecutor.renderResultAsJsonFile(jsonGenerationRequest);

            var htmlGenerationRequest = new SqlExecutor.HtmlTableRequest(request.getSql(), outputPath,
                    request.getParameterValues(), request.getDatasourceName(), request.getMarkdownFileName(),
                    request.getCodeBlockCounter());
            String htmlTable = sqlExecutor.convertToHtmlTable(htmlGenerationRequest);

            return ResponseEntity.ok(htmlTable);
        } catch (Exception e) {
            log.error("Error executing SQL", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error executing SQL: " + e.getMessage());
        }
    }

    @GetMapping("/downloadExcel")
    public ResponseEntity<Resource> downloadExcel(@RequestParam String outputFileName, @RequestParam String markdownFileName) {
        File outputFile;
        try {
            outputFile = sqlExecutor.getResourceResponseEntity(outputFileName, markdownFileName);
            FileSystemResource resource = new FileSystemResource(outputFile);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=sql_results.xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(resource.contentLength())
                    .body(resource);
        } catch (Exception e) {
            log.error("Error generating Excel file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }


    @PostMapping(value = "/sortTable", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> sortTable(@RequestBody TableSortRequest request) {
        try {
            var outputPath = Paths.get(configService.getDocsDirectory())
                    .resolve(Path.of(request.getMarkdownFileName())
                            .getParent()
                            .resolve(request.getOutputFileName()));
            JsonNode rootNode = objectMapper.readTree(outputPath.toFile());
            JsonNode dataNode = rootNode.get("data");

            var columnName = request.getColumnName();
            var columnType = request.getColumnType();
            var sortDirection = request.getSortDirection();

            List<Map<String, Object>> data = new ArrayList<>();
            for (JsonNode row : dataNode) {
                Map<String, Object> rowData = new HashMap<>();
                row.fields().forEachRemaining(entry -> rowData.put(entry.getKey(), entry.getValue().isNull() ? null : entry.getValue()));
                data.add(rowData);
            }

            sqlExecutor.sortData(data, columnName, columnType, sortDirection);

            // Convert sorted data back to JSON
            ArrayNode sortedDataNode = objectMapper.createArrayNode();
            data.forEach(row -> sortedDataNode.add(objectMapper.valueToTree(row)));

            ((ObjectNode) rootNode).set("data", sortedDataNode);

            // Write the sorted data back to the file
            objectMapper.writeValue(outputPath.toFile(), rootNode);

            String htmlTable = sqlExecutor.convertToHtmlTable(rootNode, request.getDatasourceName(),
                    request.getMarkdownFileName(), request.getOutputFileName(), request.getColumnName(),
                    request.getSortDirection(), request.getCodeBlockCounter());

            return ResponseEntity.ok(htmlTable);
        } catch (Exception e) {
            log.error("Error sorting table", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error sorting table " + e.getMessage());
        }
    }

    @PostMapping("/saveSqlChanges")
    public ResponseEntity<Map<String, Object>> saveSqlChanges(@RequestBody SqlChangeRequest request) {
        try {
            Path markdownPath = Paths.get(configService.getDocsDirectory())
                    .resolve(request.markdownFileName);

            String markdownContent = Files.readString(markdownPath);
            String[] lines = markdownContent.split("\n");

            List<String> linesUptoNthCodeblock = linesUptoNthCodeBlock(lines, request.codeBlockCounter);
            List<String> linesAfterNthCodeblock = linesAfterNthCodeBlock(linesUptoNthCodeblock.size(), lines, request.codeBlockCounter);
            List<String> updatedLines = combineLines(linesUptoNthCodeblock, request.newSql, linesAfterNthCodeblock);

            String updatedContent = String.join("\n", updatedLines);
            Files.writeString(markdownPath, updatedContent);

            return ResponseEntity.ok(Map.of("success", true, "message", "SQL changes saved successfully"));
        } catch (Exception e) {
            log.error("Error saving SQL changes", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error saving changes: " + e.getMessage()));
        }
    }

    private List<String> linesUptoNthCodeBlock(String[] lines, int n) {
        List<String> result = new ArrayList<>();
        int codeBlockCount = 0;
        boolean insideCodeBlock = false;
        for (String line : lines) {
            result.add(line);
            if (line.startsWith("```")) {
                if (insideCodeBlock) {
                    insideCodeBlock = false;
                } else {
                    insideCodeBlock = true;
                    codeBlockCount++;
                    if (codeBlockCount == n) {
                        break;
                    }
                }
            }
        }
        return result;
    }

    private List<String> linesAfterNthCodeBlock(int startIndex, String[] lines, int n) {
        // skip the first n lines
        List<String> result = new ArrayList<>();
        // find end of code block
        int counter = 0;
        for (int i = startIndex; i < lines.length; i++) {
            counter++;
            if (lines[i].startsWith("```")) {
                break;
            }
        }
        for (int i = startIndex + counter; i < lines.length; i++) {
            result.add(lines[i]);
        }
        return result;
    }

    private List<String> combineLines(List<String> before, String newSql, List<String> after) {
        List<String> result = new ArrayList<>(before);
        result.addAll(List.of(newSql.split("\n")));
        result.add("```");
        result.addAll(after);
        return result;
    }

    @lombok.Data
    private static class SqlChangeRequest {
        private String markdownFileName;
        private String oldSql;
        private String newSql;
        private Integer codeBlockCounter;
    }

    @lombok.Data
    public static class SqlExecutionRequest {
        private String datasourceName;
        private String sql;
        private String markdownFileName;
        private Integer codeBlockCounter;
        private Map<String, String> parameterValues;
        private boolean forceExecute;
    }

    @lombok.Data
    public static class TableSortRequest {
        private String columnName;
        private String columnType;
        private String outputFileName;
        private String datasourceName;
        private String markdownFileName;
        private String sortDirection;
        private Integer codeBlockCounter;
    }
}
