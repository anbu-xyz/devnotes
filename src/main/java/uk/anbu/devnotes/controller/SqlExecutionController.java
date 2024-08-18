package uk.anbu.devnotes.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
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

    @PostMapping(value = "/reExecuteSql", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> reExecuteSql(@RequestBody SqlExecutionRequest request) {
        String dataSourceName = request.getDatasourceName();
        ConfigService.DataSourceConfig dataSourceConfig = configService.getDataSourceConfig(dataSourceName);

        if (dataSourceConfig == null) {
            log.error("Invalid datasource name: {}", dataSourceName);
            return ResponseEntity.badRequest().body("Invalid datasource name:" + dataSourceName);
        }

        var outputPath = sqlExecutor.executeSqlAndSaveOutput(dataSourceConfig, request.getSql(),
                request.getParameterValues(), request.getMarkdownFileName());

        String htmlTable = sqlExecutor.convertToHtmlTable(request.getSql(), outputPath,
                request.getParameterValues(), request.getDatasourceName(), request.getMarkdownFileName());

        return ResponseEntity.ok(htmlTable);
    }

    @GetMapping("/downloadExcel")
    public ResponseEntity<Resource> downloadExcel(@RequestParam String fileName) {
        File outputFile;
        try {
            outputFile = sqlExecutor.getResourceResponseEntity(fileName);
            FileSystemResource resource = new FileSystemResource(outputFile);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=sql_results.xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(resource.contentLength())
                    .body(resource);
        } catch (IOException e) {
            log.error("Error generating Excel file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
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

            List<Map<String, Object>> data = new ArrayList<>();
            for (JsonNode row : dataNode) {
                Map<String, Object> rowData = new HashMap<>();
                row.fields().forEachRemaining(entry -> rowData.put(entry.getKey(), entry.getValue()));
                data.add(rowData);
            }

            data.sort((a, b) -> {
                int comparison = compare(a.get(request.getColumnName()), b.get(request.getColumnName()), request.getColumnType());
                return request.getSortDirection().equals("desc") ? -comparison : comparison;
            });

            // Convert sorted data back to JSON
            ArrayNode sortedDataNode = objectMapper.createArrayNode();
            data.forEach(row -> sortedDataNode.add(objectMapper.valueToTree(row)));

            // Replace the original data with sorted data
            ((ObjectNode) rootNode).set("data", sortedDataNode);

            // Write the sorted data back to the file
            objectMapper.writeValue(outputPath.toFile(), rootNode);

            // Convert to HTML table
            String htmlTable = sqlExecutor.convertToHtmlTable(rootNode, request.getDatasourceName(),
                    request.getMarkdownFileName(), request.getOutputFileName(), request.getColumnName(), request.getSortDirection());

            return ResponseEntity.ok(htmlTable);
        } catch (IOException e) {
            log.error("Error sorting table", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error sorting table");
        }
    }

    private int compare(Object a, Object b, String dataType) {
        if (a == null || b == null) {
            return a == null ? (b == null ? 0 : -1) : 1;
        }
        switch (dataType) {
            case "java.lang.Integer":
                var intA = Integer.parseInt(a.toString());
                var intB = Integer.parseInt(b.toString());
                return Integer.compare(intA, intB);
            case "java.lang.Long":
                var longA = Long.parseLong(a.toString());
                var longB = Long.parseLong(b.toString());
                return Long.compare(longA, longB);
            case "java.lang.Double":
                var doubleA = Double.parseDouble(a.toString());
                var doubleB = Double.parseDouble(b.toString());
                return Double.compare(doubleA, doubleB);
            case "java.sql.Timestamp":
                var timestampA = Timestamp.valueOf(a.toString());
                var timestampB = Timestamp.valueOf(b.toString());
                return timestampA.compareTo(timestampB);
            default:
                return a.toString().compareTo(b.toString());
        }
    }

    @lombok.Data
    public static class SqlExecutionRequest {
        private String datasourceName;
        private String sql;
        private String markdownFileName;
        private Map<String, String> parameterValues;
    }

    @lombok.Data
    public static class TableSortRequest {
        private String columnName;
        private String columnType;
        private String outputFileName;
        private String datasourceName;
        private String markdownFileName;
        private String sortDirection;
    }
}
