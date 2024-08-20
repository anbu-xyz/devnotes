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

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static uk.anbu.devnotes.module.MarkdownRenderer.generateOutputFileName;

@RequiredArgsConstructor
@Slf4j
@Component
public class SqlExecutor {

    private final ObjectMapper objectMapper;
    private final TemplateEngine templateEngine;
    private final ConfigService configService;

    public Path executeSqlAndSaveOutput(ConfigService.DataSourceConfig config, String sql,
                                        Map<String, String> parameterValues, String markdownFilePath) {
        var jdbcTemplate = getNamedParameterJdbcTemplate(config);

        String outputFileName = generateOutputFileName(configService.getDocsDirectory(),
                markdownFilePath, sql + parameterValues.toString());
        Path outputPath = Paths.get(outputFileName);

        SqlParameterSource parameterSource = new MapSqlParameterSource(parameterValues);

        final Integer[] rowCount = {0};
        final Integer[] columnCount = {0};
        final List<String> columnNames = new ArrayList<>();
        try (FileWriter writer = new FileWriter(outputPath.toFile())) {
            JsonGenerator jsonGenerator = objectMapper.getFactory().createGenerator(writer);
            startOutermostObject(jsonGenerator);

            // Write SQL information
            jsonGenerator.writeObjectFieldStart("sql");
            jsonGenerator.writeStringField("sqlText", sql);
            jsonGenerator.writeObjectFieldStart("parameterValues");
            for (Map.Entry<String, String> entry : parameterValues.entrySet()) {
                jsonGenerator.writeStringField(entry.getKey(), entry.getValue());
            }
            jsonGenerator.writeEndObject(); // end parameterValues
            jsonGenerator.writeEndObject(); // end sql

            jsonGenerator.writeStringField("datasourceName", config.name());

            jdbcTemplate.query(sql, parameterSource, (rs) -> {
                try {
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

    private static NamedParameterJdbcTemplate getNamedParameterJdbcTemplate(ConfigService.DataSourceConfig dataSourceConfig) {

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(dataSourceConfig.driverClassName());
        dataSource.setUrl(dataSourceConfig.url());
        dataSource.setUsername(dataSourceConfig.username());
        dataSource.setPassword(dataSourceConfig.password());

        var jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setFetchSize(500);

        return new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    public File getResourceResponseEntity(String fileName) throws IOException {
        Path outputPath = Paths.get(configService.getDocsDirectory()).resolve(fileName);
        JsonNode rootNode = objectMapper.readTree(outputPath.toFile());

        JsonNode sqlNode = rootNode.get("sql");
        String sql = sqlNode.get("sqlText").asText();
        Map<String, Object> parameterValues = objectMapper.convertValue(sqlNode.get("parameterValues"),
                new TypeReference<>() {
                });

        String dataSourceName = rootNode.get("datasourceName").asText();
        ConfigService.DataSourceConfig dataSourceConfig = configService.getDataSourceConfig(dataSourceName);

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
            jsonGenerator.writeObjectField(columnNames.get(i - 1), rs.getObject(i));
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

    public String convertToHtmlTable(String sqlText, Path outputPath, Map<String, String> parameterValues,
                                     String dataSourceName, String markdownFileName) {
        try {
            JsonNode rootNode = objectMapper.readTree(outputPath.toFile());
            JsonNode metadataNode = rootNode.get("metadata");
            JsonNode dataNode = rootNode.get("data");

            SqlResult.Data sqlResult = getResult(metadataNode, dataNode);

            Map<String, Object> params = new HashMap<>();
            params.put("outputFileName", outputPath.getFileName().toString());
            params.put("datasourceName", dataSourceName);
            params.put("markdownFileName", markdownFileName);
            params.put("sqlResult", SqlResult.builder()
                    .sql(new SqlResult
                            .Sql(sqlText, Map.of())) // TODO: populate parameters
                    .datasourceName(dataSourceName)
                    .hasMoreRows(false) // TODO
                    .data(sqlResult)
                    .build());

            StringOutput output = new StringOutput();
            templateEngine.render("sql-result-table.jte", params, output);
            return output.toString();

        } catch (IOException e) {
            log.error("Error rendering SQL result table", e);
            return "Error: Unable to render SQL result table";
        }
    }

    public String convertToHtmlTable(JsonNode rootNode, String dataSourceName, String markdownFileName,
                                     String outputFileName, String sortColumn, String sortDirection) {
        JsonNode metadataNode = rootNode.get("metadata");
        JsonNode dataNode = rootNode.get("data");
        JsonNode sqlNode = rootNode.get("sql");

        String previousSqlText = sqlNode.get("sqlText").asText();
        Map<String, String> previousParameterValues = objectMapper.convertValue(sqlNode.get("parameterValues"),
                new TypeReference<>() {
                });

        SqlResult.Data sqlResult = getResult(metadataNode, dataNode);

        Map<String, Object> params = new HashMap<>();
        params.put("outputFileName", outputFileName);
        params.put("datasourceName", dataSourceName);
        params.put("markdownFileName", markdownFileName);
        params.put("parameterValues", previousParameterValues);
        params.put("sortColumn", sortColumn);
        params.put("sortDirection", sortDirection);
        params.put("sqlResult", SqlResult.builder()
                .sql(new SqlResult
                        .Sql(previousSqlText, Map.of())) // TODO: populate parameters
                        .datasourceName(dataSourceName)
                        .hasMoreRows(false) // TODO
                        .data(sqlResult)
                .build());

        StringOutput output = new StringOutput();
        templateEngine.render("sql-result-table.jte", params, output);
        return output.toString();
    }

    private static SqlResult.Data getResult(JsonNode metadataNode, JsonNode dataNode) {
        List<SqlResult.Metadata> metadata = new ArrayList<>();
        for (JsonNode column : metadataNode) {
            metadata.add(new SqlResult.Metadata(column.get("name").asText(), column.get("type").asText()));
        }

        List<Map<String, Object>> data = new ArrayList<>();
        for (JsonNode row : dataNode) {
            Map<String, Object> rowData = new HashMap<>();
            for (var colMeta : metadata) {
                String columnName = colMeta.name();
                String stringValue = row.get(columnName).textValue(); // default is to String
                Object value = switch (colMeta.javaClass()) {
                    case "java.lang.Integer" -> row.get(columnName).intValue();
                    case "java.lang.Long" -> row.get(columnName).longValue();
                    case "java.lang.Double" -> row.get(columnName).doubleValue();
//                    case "java.sql.Date" -> java.sql.Date.valueOf(row.get(columnName).toString());
                    case "java.math.BigDecimal" -> new java.math.BigDecimal(row.get(columnName).toString());
                    default -> stringValue;
                };
                rowData.put(columnName, value);
            }
            data.add(rowData);
        }
        return new SqlResult.Data(metadata, data);
    }

}
