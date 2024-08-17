package uk.anbu.devtools.module;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;
import uk.anbu.devtools.service.ConfigService;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.ResultSetMetaData;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
@Component
public class SqlExecutor {

    private final ObjectMapper objectMapper;

    public void executeSqlAndSaveOutput(ConfigService.DataSourceConfig config, String sql,
                                        Map<Integer, String> parameterValues, Path outputPath) {

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(config.driverClassName());
        dataSource.setUrl(config.url());
        dataSource.setUsername(config.username());
        dataSource.setPassword(config.password());

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        PreparedStatementSetter setter = ps -> {
            for (Map.Entry<Integer, String> entry : parameterValues.entrySet()) {
                ps.setObject(entry.getKey(), entry.getValue());
            }
        };

        try (FileWriter writer = new FileWriter(outputPath.toFile())) {
            jdbcTemplate.query(sql, setter, (rs) -> {
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
    }

    public String convertToHtmlTable(String sqlText, Path outputPath, Map<Integer, String> parameterValues) {
        try {
            JsonNode rootNode = objectMapper.readTree(outputPath.toFile());
            JsonNode metadataNode = rootNode.get("metadata");
            JsonNode dataNode = rootNode.get("data");

            StringBuilder htmlBuilder = new StringBuilder();
            htmlBuilder.append("<div class='sql-result'>");

            // Add download button
            htmlBuilder.append("<button class='download-btn' onclick='downloadExcel(\"")
                    .append(outputPath.getFileName()).append("\")'>Download Excel</button>");

            // Display parameters
            if (!parameterValues.isEmpty()) {
                htmlBuilder.append("<div class='parameters'><h4>Parameters:</h4><ul>");
                for (Map.Entry<Integer, String> entry : parameterValues.entrySet()) {
                    htmlBuilder.append("<li>").append(entry.getKey()).append(": ").append(entry.getValue()).append("</li>");
                }
                htmlBuilder.append("</ul></div>");
            }

            // Add re-execute button
            htmlBuilder.append("<div class='sql-reexecute-button'>\n");
            htmlBuilder.append("<button class='re-execute-btn' onclick='reExecuteSql(closest(\"div.sql-reexecute-button\"))'>Re-execute SQL</button>\n");
            htmlBuilder.append("<p class=\"sql-text\">").append(sqlText).append("</p>");
            htmlBuilder.append("</div>\n");

            htmlBuilder.append("<table class='sortable'><thead><tr>");
            for (JsonNode column : metadataNode) {
                htmlBuilder.append("<th data-type='").append(column.get("type").asText()).append("'>")
                        .append(column.get("name").asText()).append("</th>");
            }
            htmlBuilder.append("</tr></thead><tbody>");

            for (JsonNode row : dataNode) {
                htmlBuilder.append("<tr>");
                for (JsonNode column : metadataNode) {
                    String columnName = column.get("name").asText();
                    htmlBuilder.append("<td>").append(row.get(columnName).asText()).append("</td>");
                }
                htmlBuilder.append("</tr>");
            }
            htmlBuilder.append("</tbody></table></div>");
            return htmlBuilder.toString();

        } catch (IOException e) {
            log.error("Error rendering SQL result table", e);
            return "Error: Unable to render SQL result table";
        }
    }

}
