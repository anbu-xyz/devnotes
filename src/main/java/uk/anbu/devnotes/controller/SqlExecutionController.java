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
import uk.anbu.devnotes.service.DataSourceConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        DataSourceConfig dataSourceConfig = configService.getDataSourceConfig(dataSourceName);

        if (dataSourceConfig == null) {
            log.error("Invalid datasource name: {}", dataSourceName);
            return ResponseEntity.badRequest().body("Invalid datasource name:" + dataSourceName);
        }

        var jsonGenerationRequest = new SqlExecutor.JsonGenerationRequest(dataSourceConfig, request.getSql(),
                request.getParameterValues(), request.getMarkdownFileName());
        var outputPath = sqlExecutor.renderResultAsJsonFile(jsonGenerationRequest);

        var htmlGenerationRequest = new SqlExecutor.HtmlTableRequest(request.getSql(), outputPath,
                request.getParameterValues(), request.getDatasourceName(), request.getMarkdownFileName());
        String htmlTable = sqlExecutor.convertToHtmlTable(htmlGenerationRequest);

        return ResponseEntity.ok(htmlTable);
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

    @PostMapping("/saveSqlChanges")
    public ResponseEntity<Map<String, Object>> saveSqlChanges(@RequestBody SqlChangeRequest request) {
        try {
            Path markdownPath = Paths.get(configService.getDocsDirectory())
                    .resolve(request.markdownFileName);

            String markdownContent = Files.readString(markdownPath);
            String escapedOldSql = Pattern.quote(request.oldSql.trim());
            Pattern pattern = Pattern.compile("(```sql\\s*\\n)" + escapedOldSql + "(\\n```)", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(markdownContent);

            if (matcher.find()) {
                String updatedContent = matcher.replaceFirst("$1" + Matcher.quoteReplacement(request.newSql.trim()) + "$2");
                Files.writeString(markdownPath, updatedContent);

                return ResponseEntity.ok(Map.of("success", true));
            } else {
                log.error("Original SQL block not found in the markdown file");
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "message", "Original SQL block not found"));
            }
        } catch (IOException e) {
            log.error("Error saving SQL changes", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error saving changes"));
        }
    }

    @lombok.Data
    private static class SqlChangeRequest {
        private String markdownFileName;
        private String oldSql;
        private String newSql;
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
