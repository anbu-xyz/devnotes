package uk.anbu.devtools.module;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import uk.anbu.devtools.service.ConfigService;

import javax.sql.DataSource;
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

import static uk.anbu.devtools.module.MarkdownRenderer.generateOutputFileName;

@RequiredArgsConstructor
@Slf4j
@Component
public class SqlExecutor {

    private final ObjectMapper objectMapper;
    private final TemplateEngine templateEngine;
    private final ConfigService configService;

    public Path executeSqlAndSaveOutput(ConfigService.DataSourceConfig config, String sql,
                                        Map<String, String> parameterValues, String markdownFilePath) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(config.driverClassName());
        dataSource.setUrl(config.url());
        dataSource.setUsername(config.username());
        dataSource.setPassword(config.password());

        return writeSqlResultToFile(dataSource, sql, parameterValues, markdownFilePath, config.name());
    }

    public Path writeSqlResultToFile(DataSource dataSource, String sql, Map<String, String> parameterValues,
                                     String fileNameWithRelativePath, String dataSourceName) {

        String outputFileName = generateOutputFileName(configService.getDocsDirectory(),
                fileNameWithRelativePath, sql + parameterValues.toString());
        Path outputPath = Paths.get(outputFileName);

        JdbcTemplate jdbctmpl = new JdbcTemplate(dataSource);
        jdbctmpl.setFetchSize(500);
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(jdbctmpl);

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

            Assert.isTrue(dataSourceName != null, "dataSourceName must not be null");
            jsonGenerator.writeStringField("datasourceName", dataSourceName);

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

    private static void endOutermostObject(JsonGenerator jsonGenerator) throws IOException {
        jsonGenerator.writeEndObject();
    }

    private static void startOutermostObject(JsonGenerator jsonGenerator) throws IOException {
        jsonGenerator.writeStartObject();
    }

    private static void writeRow(ResultSet rs, JsonGenerator jsonGenerator, Integer[] columnCount, List<String> columnNames) throws IOException, SQLException {
        jsonGenerator.writeStartObject();
        for (int i = 1; i <= columnCount[0]; i++) {
            jsonGenerator.writeObjectField(columnNames.get(i - 1), rs.getObject(i));
        }
        jsonGenerator.writeEndObject();
    }

    private static void writeMetadata(JsonGenerator jsonGenerator, Integer[] columnCount, ResultSetMetaData metaData, List<String> columnNames) throws IOException, SQLException {
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

            SqlResult sqlResult = getResult(metadataNode, dataNode);

            Map<String, Object> params = new HashMap<>();
            params.put("sqlText", sqlText);
            params.put("outputFileName", outputPath.getFileName().toString());
            params.put("datasourceName", dataSourceName);
            params.put("markdownFileName", markdownFileName);
            params.put("parameterValues", parameterValues);
            params.put("metadata", sqlResult.metadata());
            params.put("data", sqlResult.data());

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
                new TypeReference<>() {});

        SqlResult sqlResult = getResult(metadataNode, dataNode);

        Map<String, Object> params = new HashMap<>();
        params.put("sqlText", previousSqlText);
        params.put("outputFileName", outputFileName);
        params.put("datasourceName", dataSourceName);
        params.put("markdownFileName", markdownFileName);
        params.put("parameterValues", previousParameterValues);
        params.put("metadata", sqlResult.metadata());
        params.put("data", sqlResult.data());
        params.put("sortColumn", sortColumn);
        params.put("sortDirection", sortDirection);

        StringOutput output = new StringOutput();
        templateEngine.render("sql-result-table.jte", params, output);
        return output.toString();
    }

    private static SqlResult getResult(JsonNode metadataNode, JsonNode dataNode) {
        List<Map<String, String>> metadata = new ArrayList<>();
        for (JsonNode column : metadataNode) {
            Map<String, String> columnInfo = new HashMap<>();
            columnInfo.put("name", column.get("name").asText());
            columnInfo.put("type", column.get("type").asText());
            metadata.add(columnInfo);
        }

        List<Map<String, String>> data = new ArrayList<>();
        for (JsonNode row : dataNode) {
            Map<String, String> rowData = new HashMap<>();
            for (JsonNode column : metadataNode) {
                String columnName = column.get("name").asText();
                rowData.put(columnName, row.get(columnName).asText());
            }
            data.add(rowData);
        }
        return new SqlResult(metadata, data);
    }

    private record SqlResult(List<Map<String, String>> metadata, List<Map<String, String>> data) {
    }
}
