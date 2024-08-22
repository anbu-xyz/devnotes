package uk.anbu.devnotes.module;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;
import uk.anbu.devnotes.service.ConfigService;
import uk.anbu.devnotes.service.DataSourceConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static uk.anbu.devnotes.module.MarkdownRenderer.generateOutputFileName;

@RequiredArgsConstructor
@Slf4j
@Component
public class SqlExecutor {

    private final ObjectMapper objectMapper;
    private final TemplateEngine templateEngine;
    private final ConfigService configService;

    public Path renderResultAsJsonFile(JsonGenerationRequest request) {
        var jdbcTemplate = getNamedParameterJdbcTemplate(request.dataSourceConfig());

        String outputFileName = generateOutputFileName(configService.getDocsDirectory(),
                request.markdownFilePath(), request.sql() + request.parameterValues().toString());
        Path outputPath = Paths.get(outputFileName);

        SqlParameterSource parameterSource = new MapSqlParameterSource(request.parameterValues());

        final Integer[] rowCount = {0};
        final Integer[] columnCount = {0};
        final List<String> columnNames = new ArrayList<>();
        final Boolean[] hasReachedMaxRows = {false};
        try (FileWriter writer = new FileWriter(outputPath.toFile())) {
            JsonGenerator jsonGenerator = objectMapper.getFactory().createGenerator(writer);
            startOutermostObject(jsonGenerator);

            // Write SQL information
            jsonGenerator.writeObjectFieldStart("sql");
            jsonGenerator.writeStringField("sqlText", request.sql());
            jsonGenerator.writeObjectFieldStart("parameterValues");
            for (Map.Entry<String, String> entry : request.parameterValues().entrySet()) {
                jsonGenerator.writeStringField(entry.getKey(), entry.getValue());
            }
            jsonGenerator.writeEndObject(); // end parameterValues
            jsonGenerator.writeEndObject(); // end sql

            jsonGenerator.writeStringField("datasourceName", request.dataSourceConfig().name());

            jdbcTemplate.query(request.sql(), parameterSource, (rs) -> {
                try {
                    if (rowCount[0] + 1 > configService.getSqlMaxRows()) {
                        log.info("Reached max rows, stopping SQL query");
                        hasReachedMaxRows[0] = true;
                        return;
                    }
                    rowCount[0]++;
                    if (rowCount[0] == 1) {
                        ResultSetMetaData metaData = rs.getMetaData();
                        columnCount[0] = metaData.getColumnCount();
                        writeMetadata(jsonGenerator, columnCount, metaData, columnNames);
                        jsonGenerator.writeArrayFieldStart("data");
                    }

                    writeRow(rs, jsonGenerator, columnCount, columnNames);
                } catch (IOException e) {
                    throw new RuntimeException("Error writing SQL result to file", e);
                }
            });
            jsonGenerator.writeEndArray();
            jsonGenerator.writeBooleanField("hasReachedMaxRows", hasReachedMaxRows[0]);
            endOutermostObject(jsonGenerator);
            jsonGenerator.close();
        } catch (Exception e) {
            log.error("Error executing SQL query", e);
            // write a json result with error message to the output file
            writeErrorMessage(e, outputPath);
        }
        return outputPath;
    }

    private void writeErrorMessage(Exception e, Path outputPath) {
        try (FileWriter writer = new FileWriter(outputPath.toFile())) {
            JsonGenerator jsonGenerator = objectMapper.getFactory().createGenerator(writer);
            startOutermostObject(jsonGenerator);
            jsonGenerator.writeArrayFieldStart("metadata");
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringField("name", "Error");
            jsonGenerator.writeStringField("type", "java.lang.String");
            jsonGenerator.writeEndObject();
            jsonGenerator.writeEndArray();
            jsonGenerator.writeArrayFieldStart("data");
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringField("Error", e.getMessage());
            jsonGenerator.writeEndObject();
            jsonGenerator.writeEndArray();
            endOutermostObject(jsonGenerator);
            jsonGenerator.close();
        } catch (Exception e1) {
            log.error("Error writing error message to file", e1);
        }
    }

    private NamedParameterJdbcTemplate getNamedParameterJdbcTemplate(DataSourceConfig dataSourceConfig) {

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(dataSourceConfig.driverClassName());
        dataSource.setUrl(dataSourceConfig.url());
        dataSource.setUsername(dataSourceConfig.username());
        dataSource.setPassword(dataSourceConfig.password());

        var jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setFetchSize(500);
        jdbcTemplate.setMaxRows(configService.getSqlMaxRows() + 1);

        return new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    public File getResourceResponseEntity(String fileName, String markdownFileName) throws IOException {
        Path markdownFileDirectory = Paths.get(configService.getDocsDirectory()).resolve(markdownFileName).getParent();
        Path outputPath = Paths.get(markdownFileDirectory.toString(), fileName);
        JsonNode rootNode = objectMapper.readTree(outputPath.toFile());

        JsonNode sqlNode = rootNode.get("sql");
        String sql = sqlNode.get("sqlText").asText();
        Map<String, Object> parameterValues = objectMapper.convertValue(sqlNode.get("parameterValues"),
                new TypeReference<>() {
                });

        String dataSourceName = rootNode.get("datasourceName").asText();
        DataSourceConfig dataSourceConfig = configService.getDataSourceConfig(dataSourceName);

        NamedParameterJdbcTemplate jdbcTemplate = getNamedParameterJdbcTemplate(dataSourceConfig);

        return createWorkbook(jdbcTemplate, sql, parameterValues);
    }

    private static File createWorkbook(NamedParameterJdbcTemplate jdbcTemplate, String sql,
                                       Map<String, Object> parameterValues) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("SQL Results");

        CellStyle headerStyle = createHeaderRowStyle(workbook);

        final int[] rowNum = {0};
        final boolean[] headerCreated = {false};

        jdbcTemplate.query(sql, parameterValues, rs -> {
            if (!headerCreated[0]) {
                createHeaderRow(rs, sheet, rowNum, headerStyle, headerCreated);
            }
            createDataRow(rs, sheet, rowNum);
        });

        for (int i = 0; i < sheet.getRow(0).getPhysicalNumberOfCells(); i++) {
            sheet.autoSizeColumn(i);
        }

        File outputFile = File.createTempFile("sql_results", ".xlsx");
        FileOutputStream outputStream = new FileOutputStream(outputFile);
        workbook.write(outputStream);
        workbook.close();
        return outputFile;
    }

    private static CellStyle createHeaderRowStyle(Workbook workbook) {
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        return headerStyle;
    }

    private static void createDataRow(ResultSet rs, Sheet sheet, int[] rowNum) throws SQLException {
        Row dataRow = sheet.createRow(rowNum[0]++);
        for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
            Cell cell = dataRow.createCell(i - 1);
            Object value = rs.getObject(i);
            if (value != null) {
                cell.setCellValue(value.toString());
            }
        }
    }

    private static void createHeaderRow(ResultSet rs, Sheet sheet, int[] rowNum, CellStyle headerStyle,
                                        boolean[] headerCreated) throws SQLException {
        Row headerRow = sheet.createRow(rowNum[0]++);
        for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
            Cell cell = headerRow.createCell(i - 1);
            cell.setCellValue(rs.getMetaData().getColumnName(i));
            cell.setCellStyle(headerStyle);
        }
        headerCreated[0] = true;
    }


    private static void endOutermostObject(JsonGenerator jsonGenerator) throws IOException {
        jsonGenerator.writeEndObject();
    }

    private static void startOutermostObject(JsonGenerator jsonGenerator) throws IOException {
        jsonGenerator.writeStartObject();
    }

    private static void writeRow(ResultSet rs, JsonGenerator jsonGenerator, Integer[] columnCount,
                                 List<String> columnNames) throws IOException, SQLException {
        jsonGenerator.writeStartObject();
        for (int i = 1; i <= columnCount[0]; i++) {
            // check if the value is a blob and if so, put a placeholder in the JSON
            if (rs.getObject(i) instanceof Blob) {
                jsonGenerator.writeObjectField(columnNames.get(i - 1), "<blob>");
            } else if (rs.getObject(i) instanceof Clob) {
                jsonGenerator.writeObjectField(columnNames.get(i - 1), "<clob>");
            }
            else {
                jsonGenerator.writeObjectField(columnNames.get(i - 1), rs.getObject(i));
            }
        }
        jsonGenerator.writeEndObject();
    }

    private static void writeMetadata(JsonGenerator jsonGenerator, Integer[] columnCount, ResultSetMetaData metaData,
                                      List<String> columnNames) throws IOException, SQLException {
        jsonGenerator.writeArrayFieldStart("metadata");
        for (int i = 1; i <= columnCount[0]; i++) {
            jsonGenerator.writeStartObject();
            var columnLabel = metaData.getColumnLabel(i);
            jsonGenerator.writeStringField("name", columnLabel);
            columnNames.add(columnLabel);
            jsonGenerator.writeStringField("type", metaData.getColumnClassName(i));
            jsonGenerator.writeEndObject();
        }
        jsonGenerator.writeEndArray();
    }

    public String convertToHtmlTable(HtmlTableRequest request) {
        try {

            SqlResult sqlResult = getResult(request);

            Map<String, Object> params = new HashMap<>();
            params.put("outputFileName", request.outputPath().getFileName().toString());
            params.put("datasourceName", request.dataSourceName());
            params.put("markdownFileName", request.markdownFileName());
            params.put("sqlResult", sqlResult);

            StringOutput output = new StringOutput();
            templateEngine.render("sql-result-table.jte", params, output);
            return output.toString();

        } catch (IOException e) {
            log.error("Error rendering SQL result table", e);
            return "Error: Unable to render SQL result table";
        }
    }

    public String convertToHtmlTable(JsonNode rootNode, String dataSourceName, String markdownFileName,
                                     String outputFileName, String sortColumn, String sortDirection) throws IOException {
        JsonNode sqlNode = rootNode.get("sql");
        String previousSqlText = sqlNode.get("sqlText").asText();

        Map<String, String> previousParameterValues = objectMapper.convertValue(sqlNode.get("parameterValues"),
                new TypeReference<>() {
                });

        var outputPath = Path.of(configService.getDocsDirectory()).resolve(markdownFileName).getParent()
                .resolve(outputFileName);
        HtmlTableRequest request = new HtmlTableRequest(previousSqlText, outputPath, previousParameterValues,
                dataSourceName, markdownFileName);
        SqlResult sqlResult = getResult(request);

        Map<String, Object> params = new HashMap<>();
        params.put("outputFileName", outputFileName);
        params.put("datasourceName", dataSourceName);
        params.put("markdownFileName", markdownFileName);
        params.put("parameterValues", previousParameterValues);
        params.put("sortColumn", sortColumn);
        params.put("sortDirection", sortDirection);
        params.put("sqlResult", sqlResult);

        StringOutput output = new StringOutput();
        templateEngine.render("sql-result-table.jte", params, output);
        return output.toString();
    }

    public record HumanReadableNumber(String value) {
        @Override
        public String toString() {
            return value;
        }
    }

    static HumanReadableNumber humanReadableNumber(Number value) {
        if (value == null) {
            return new HumanReadableNumber("(null)");
        }

        // Convert the number to BigDecimal to handle all types of numbers
        var bigDecimal = new BigDecimal(value.toString());

        // Create a DecimalFormat instance
        var symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(',');
        symbols.setDecimalSeparator('.');
        DecimalFormat df = new DecimalFormat("#,##0.##################", symbols);
        df.setMaximumFractionDigits(340); // Maximum possible double precision

        // Format the number
        String formattedNumber = df.format(bigDecimal);

        // Handle special cases for very large or very small numbers
        if (bigDecimal.abs().compareTo(new BigDecimal("1E16")) >= 0 ||
                (bigDecimal.abs().compareTo(BigDecimal.ZERO) > 0 && bigDecimal.abs().compareTo(new BigDecimal("1E-3")) < 0)) {
            // Use plain string for very large or very small numbers
            formattedNumber = bigDecimal.toPlainString();

            // Insert commas for the integer part
            int dotIndex = formattedNumber.indexOf('.');
            if (dotIndex == -1) {
                dotIndex = formattedNumber.length();
            }
            StringBuilder sb = new StringBuilder(formattedNumber);
            for (int i = dotIndex - 3; i > 0; i -= 3) {
                sb.insert(i, ',');
            }
            formattedNumber = sb.toString();
        }

        return new HumanReadableNumber(formattedNumber);
    }

    SqlResult getResult(HtmlTableRequest request) throws IOException {
        JsonNode rootNode = objectMapper.readTree(request.outputPath().toFile());
        JsonNode metadataNode = rootNode.get("metadata");
        JsonNode dataNode = rootNode.get("data");
        JsonNode sqlNode = rootNode.get("sql");

        String sql = sqlNode.get("sqlText").asText();
        Map<String, Object> parameterValues = objectMapper.convertValue(sqlNode.get("parameterValues"), new TypeReference<>() {});

        List<SqlResult.Metadata> metadata = new ArrayList<>();
        for (JsonNode column : metadataNode) {
            metadata.add(new SqlResult.Metadata(column.get("name").asText(), column.get("type").asText()));
        }

        List<Map<String, Object>> data = new ArrayList<>();
        for (JsonNode row : dataNode) {
            Map<String, Object> rowData = new HashMap<>();
            for (var colMeta : metadata) {
                String columnName = colMeta.name();
                JsonNode valueNode = row.get(columnName);
                Object value;
                if (valueNode.isNull()) {
                    value = "(null)";
                } else {
                    value = switch (colMeta.javaClass()) {
                        case "java.lang.Integer", "java.lang.Long", "java.lang.Double", "java.math.BigDecimal" ->
                                humanReadableNumber(new BigDecimal(valueNode.asText()));
                        default -> valueNode.asText();
                    };
                }
                rowData.put(columnName, value);
            }
            data.add(rowData);
        }

        boolean hasReachedMaxRows = rootNode.has("hasReachedMaxRows") && rootNode.get("hasReachedMaxRows").asBoolean();

        return SqlResult.builder()
                .sql(new SqlResult.Sql(sql, parameterValues))
                .datasourceName(rootNode.get("datasourceName").asText())
                .hasReachedMaxRows(hasReachedMaxRows)
                .data(new SqlResult.Data(metadata, data))
                .build();
    }

    public static List<Map<String, Object>> sortData(List<Map<String, Object>> data, String columnName, String columnType,
                                              String sortDirection) {

        data.sort((a, b) -> {
            int comparison = compare(a.get(columnName), b.get(columnName), columnType);
            return sortDirection.equals("desc") ? -comparison : comparison;
        });
        return data;
    }

    private static int compare(Object a, Object b, String dataType) {
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
            case "java.math.BigDecimal":
                var decimalA = new BigDecimal(a.toString());
                var decimalB = new BigDecimal(b.toString());
                return decimalA.compareTo(decimalB);
            case "java.sql.Timestamp":
                var timestampA = Timestamp.valueOf(a.toString());
                var timestampB = Timestamp.valueOf(b.toString());
                return timestampA.compareTo(timestampB);
            default:
                return a.toString().compareTo(b.toString());
        }
    }

    public record JsonGenerationRequest(DataSourceConfig dataSourceConfig, String sql,
                                        Map<String, String> parameterValues, String markdownFilePath) {}

    public record HtmlTableRequest(String sqlText, Path outputPath, Map<String, String> parameterValues,
                                   String dataSourceName, String markdownFileName) {}
}