package uk.anbu.devtools.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
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
import uk.anbu.devtools.module.SqlExecutor;
import uk.anbu.devtools.service.ConfigService;

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
        // Implement Excel download logic here
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=" + fileName).body(new ClassPathResource("excel.xlsx"));
    }

    @PostMapping("/sortTable")
    public ResponseEntity<String> sortTable(@RequestParam String column,
                                            @RequestParam String dataType,
                                            @RequestParam String outputFile,
                                            @RequestParam String datasource,
                                            @RequestParam String markdownFile) {
        try {
            Path outputPath = Paths.get(configService.getDocsDirectory(), outputFile);
            JsonNode rootNode = objectMapper.readTree(outputPath.toFile());
            JsonNode metadataNode = rootNode.get("metadata");
            JsonNode dataNode = rootNode.get("data");

            List<Map<String, Object>> data = new ArrayList<>();
            for (JsonNode row : dataNode) {
                Map<String, Object> rowData = new HashMap<>();
                row.fields().forEachRemaining(entry -> rowData.put(entry.getKey(), entry.getValue()));
                data.add(rowData);
            }

            // Sort the data based on the column and data type
            data.sort((a, b) -> compare(a.get(column), b.get(column), dataType));

            // Convert sorted data back to JSON
            ArrayNode sortedDataNode = objectMapper.createArrayNode();
            data.forEach(row -> sortedDataNode.add(objectMapper.valueToTree(row)));

            // Replace the original data with sorted data
            ((ObjectNode) rootNode).set("data", sortedDataNode);

            // Write the sorted data back to the file
            objectMapper.writeValue(outputPath.toFile(), rootNode);

            // Convert to HTML table
            String htmlTable = sqlExecutor.convertToHtmlTable(rootNode, datasource, markdownFile);

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
                return Integer.compare((Integer) a, (Integer) b);
            case "java.lang.Long":
                return Long.compare((Long) a, (Long) b);
            case "java.lang.Double":
                return Double.compare((Double) a, (Double) b);
            case "java.sql.Timestamp":
                return ((Timestamp) a).compareTo((Timestamp) b);
            default:
                return a.toString().compareTo(b.toString());
        }
    }
}
