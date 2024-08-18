package uk.anbu.devtools.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devtools.module.SqlExecutor;
import uk.anbu.devtools.service.ConfigService;

import java.io.ByteArrayOutputStream;
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
        try {
            Path outputPath = Paths.get(configService.getDocsDirectory()).resolve(fileName);
            JsonNode rootNode = objectMapper.readTree(outputPath.toFile());

            JsonNode sqlNode = rootNode.get("sql");
            String sql = sqlNode.get("sqlText").asText();
            Map<String, Object> parameterValues = objectMapper.convertValue(sqlNode.get("parameterValues"), new TypeReference<>() {});

            String dataSourceName = rootNode.get("datasourceName").asText();
            ConfigService.DataSourceConfig dataSourceConfig = configService.getDataSourceConfig(dataSourceName);

            NamedParameterJdbcTemplate jdbcTemplate = getNamedParameterJdbcTemplate(dataSourceConfig, dataSourceName);

            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, parameterValues);

            // Create Excel workbook
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("SQL Results");

            // Create header row
            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            int cellNum = 0;
            for (String columnName : results.get(0).keySet()) {
                Cell cell = headerRow.createCell(cellNum++);
                cell.setCellValue(columnName);
                cell.setCellStyle(headerStyle);
            }

            // Create data rows
            int rowNum = 1;
            for (Map<String, Object> row : results) {
                Row dataRow = sheet.createRow(rowNum++);
                cellNum = 0;
                for (Object value : row.values()) {
                    Cell cell = dataRow.createCell(cellNum++);
                    if (value != null) {
                        cell.setCellValue(value.toString());
                    }
                }
            }

            // Autosize columns
            for (int i = 0; i < results.get(0).size(); i++) {
                sheet.autoSizeColumn(i);
            }

            // Write workbook to ByteArrayOutputStream
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            workbook.close();

            // Create resource from ByteArrayOutputStream
            ByteArrayResource resource = new ByteArrayResource(outputStream.toByteArray());

            // Set the content type and attachment header
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=sql_results.xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(resource.contentLength())
                    .body(resource);

        } catch (Exception e) {
            log.error("Error generating Excel file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    private static NamedParameterJdbcTemplate getNamedParameterJdbcTemplate(ConfigService.DataSourceConfig dataSourceConfig, String dataSourceName) {
        if (dataSourceConfig == null) {
            throw new IllegalArgumentException("Invalid datasource name: " + dataSourceName);
        }

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(dataSourceConfig.driverClassName());
        dataSource.setUrl(dataSourceConfig.url());
        dataSource.setUsername(dataSourceConfig.username());
        dataSource.setPassword(dataSourceConfig.password());

        var jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setFetchSize(500);

        return new NamedParameterJdbcTemplate(jdbcTemplate);
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
