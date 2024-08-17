package uk.anbu.devtools.module;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;
import uk.anbu.devtools.service.ConfigService;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSetMetaData;
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
                                        Map<String, String> parameterValues, String fileNameWithRelativePath) {

        String outputFileName = generateOutputFileName(configService.getDocsDirectory(),
                fileNameWithRelativePath, sql + parameterValues.toString());
        Path outputPath = Paths.get(outputFileName);

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(config.driverClassName());
        dataSource.setUrl(config.url());
        dataSource.setUsername(config.username());
        dataSource.setPassword(config.password());

        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);

        try (FileWriter writer = new FileWriter(outputPath.toFile())) {
            jdbcTemplate.query(sql, parameterValues, (rs) -> {
                try {
                    ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();

                    JsonGenerator jsonGenerator = objectMapper.getFactory().createGenerator(writer);
                    jsonGenerator.writeStartObject();

                    // Write metadata
                    jsonGenerator.writeArrayFieldStart("metadata");
                    for (int i = 1; i <= columnCount; i++) {
                        jsonGenerator.writeStartObject();
                        jsonGenerator.writeStringField("name", metaData.getColumnName(i));
                        jsonGenerator.writeStringField("type", metaData.getColumnTypeName(i));
                        jsonGenerator.writeEndObject();
                    }
                    jsonGenerator.writeEndArray();

                    // Write data
                    jsonGenerator.writeArrayFieldStart("data");
                    while (rs.next()) {
                        log.info("Writing row");
                        jsonGenerator.writeStartObject();
                        for (int i = 1; i <= columnCount; i++) {
                            jsonGenerator.writeObjectField(metaData.getColumnName(i), rs.getObject(i));
                        }
                        jsonGenerator.writeEndObject();
                    }
                    jsonGenerator.writeEndArray();

                    jsonGenerator.writeEndObject();
                    jsonGenerator.close();
                } catch (IOException e) {
                    throw new RuntimeException("Error writing SQL result to file", e);
                }
            });
        } catch (Exception e) {
            log.error("Error executing SQL query", e);
        }
        return outputPath;
    }

    public String convertToHtmlTable(String sqlText, Path outputPath, Map<String, String> parameterValues,
                                     String dataSourceName, String markdownFileName) {
        try {
            JsonNode rootNode = objectMapper.readTree(outputPath.toFile());
            JsonNode metadataNode = rootNode.get("metadata");
            JsonNode dataNode = rootNode.get("data");

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

            Map<String, Object> params = new HashMap<>();
            params.put("sqlText", sqlText);
            params.put("outputFileName", outputPath.getFileName().toString());
            params.put("datasourceName", dataSourceName);
            params.put("markdownFileName", markdownFileName);
            params.put("parameterValues", parameterValues);
            params.put("metadata", metadata);
            params.put("data", data);

            StringOutput output = new StringOutput();
            templateEngine.render("sql-result-table.jte", params, output);
            return output.toString();

        } catch (IOException e) {
            log.error("Error rendering SQL result table", e);
            return "Error: Unable to render SQL result table";
        }
    }
}
