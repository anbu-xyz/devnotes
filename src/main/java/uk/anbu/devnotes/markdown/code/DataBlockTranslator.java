package uk.anbu.devnotes.markdown.code;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import gg.jte.resolve.DirectoryCodeResolver;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.Node;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import uk.anbu.devnotes.service.ConfigService;
import uk.anbu.devnotes.types.MarkdownFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.function.Function;

@Slf4j
public class DataBlockTranslator {
    private final Function<String, ConfigService.DataSourceConfig> dataSourceConfigResolver;

    public DataBlockTranslator(Function<String, ConfigService.DataSourceConfig> resolver) {
        this.dataSourceConfigResolver = resolver;
    }

    @SneakyThrows
    public Optional<Node> renderDataBlock(String dataConfig, MarkdownFile markdownFile) {
        String mdName = markdownFile == null ? "" : markdownFile.fileName();

        // Parse YAML config
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        Map<String, Object> config;
        try {
            config = yamlMapper.readValue(dataConfig, new TypeReference<>() {
            });
        } catch (Exception e) {
            var html = new HtmlBlock();
            html.setLiteral("<div>Error parsing data block YAML (" + escapeHtml(mdName) + "): " + escapeHtml(e.getMessage()) + "</div>");
            return Optional.of(html);
        }

        // Resolve datasource
        String source = config.getOrDefault("source", "").toString();
        if (source.isEmpty()) {
            var html = new HtmlBlock();
            html.setLiteral("<div>Error: 'source' not specified in data block (" + escapeHtml(mdName) + ").</div>");
            return Optional.of(html);
        }

        // allow source strings like 'database/datasourceName' or just the name
        String dataSourceName = source.contains("/") ? source.substring(source.lastIndexOf('/') + 1) : source;

        ConfigService.DataSourceConfig dsConfig = null;
        if (dataSourceConfigResolver != null) {
            dsConfig = dataSourceConfigResolver.apply(dataSourceName);
        }

        if (dsConfig == null) {
            var html = new HtmlBlock();
            html.setLiteral("<div>Error: DataSource '" + escapeHtml(dataSourceName) + "' not configured (" + escapeHtml(mdName) + ").</div>");
            return Optional.of(html);
        }

        // Extract query
        String query = config.getOrDefault("query", "").toString();
        if (query.isEmpty()) {
            var html = new HtmlBlock();
            html.setLiteral("<div>Error: 'query' not specified in data block (" + escapeHtml(mdName) + ").</div>");
            return Optional.of(html);
        }

        // Extract options in a type-safe way
        Object optionsObj = config.get("options");
        Map<String, Object> options = Map.of();
        if (optionsObj instanceof Map) {
            //noinspection unchecked
            options = (Map<String, Object>) optionsObj;
        }

        int limit = readLimit(options);
        List<Map<String, Object>> rows;
        try {
            rows = buildDataRows(dsConfig, limit, query);
            cleanNullColumns(rows);
            if (rows.isEmpty() || rows.getFirst().isEmpty()) {
                return Optional.empty();
            }
        } catch (Exception e) {
            var html = new HtmlBlock();
            html.setLiteral("<div>Error executing query against datasource '" + escapeHtml(dataSourceName)
                    + "' (" + escapeHtml(mdName) + "): " + escapeHtml(e.getMessage()) + "</div>");
            return Optional.of(html);
        }

        // If columns not provided, infer from first row
        var columns = readColumnsData(options, rows);
        if (columns.size() == 1 && "false".equalsIgnoreCase(options.getOrDefault("dont-combine-single-column", "false").toString())) {
            return Optional.of(combineIfSingleColumn(columns.getFirst(), rows));
        }

        // Check for output-template
        Object outputTemplateObj = config.get("output-template");
        String outputTemplateType = config.getOrDefault("output-template-type", "").toString();
        if (outputTemplateObj != null && "jte".equalsIgnoreCase(outputTemplateType)) {
            String templateContent = outputTemplateObj.toString();
            try {
                var rendered = userProvidedTemplate(columns, rows, dataSourceName, mdName, templateContent);
                return Optional.of(rendered);
            } catch (Exception e) {
                log.error("Error rendering data block template", e);
                var html = new HtmlBlock();
                html.setLiteral("<div>Error rendering template for data block ('" + escapeHtml(mdName) + "'): " + escapeHtml(e.getMessage()) + "</div>");
                return Optional.of(html);
            }
        }

        return Optional.of(htmlFallbackTable(dataSourceName, columns, rows));
    }

    private static Node combineIfSingleColumn(String firstColumnName, List<Map<String, Object>> rows) {
        HtmlBlock html = new HtmlBlock();
        List<String> values = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object v = row.get(firstColumnName);
            values.add(v == null ? "(null)" : v.toString());
        }
        html.setLiteral("<table class=\"data-block-combined\"> <tbody><tr><td class=\"column-name\">"
                + escapeHtml(firstColumnName) + "</td><td>"
                + escapeHtml(String.join(", ", values))
                + "</td></tr></tbody></table>");
        return html;
    }

    private static void cleanNullColumns(List<Map<String, Object>> rows) {
        var allColumns = new ArrayList<>(rows.isEmpty() ? List.of() : rows.getFirst().keySet());
        for (String col : allColumns) {
            boolean allNull = true;
            for (Map<String, Object> row : rows) {
                if (row.get(col) != null) {
                    allNull = false;
                    break;
                }
            }
            if (allNull) {
                for (Map<String, Object> row : rows) {
                    row.remove(col);
                }
            }
        }
    }

    private static int readLimit(Map<String, Object> options) {
        int limit = 0;
        if (options.containsKey("limit")) {
            try {
                limit = Integer.parseInt(options.get("limit").toString());
            } catch (Exception ignored) {
            }
        }
        return limit;
    }

    private static List<String> readColumnsData(Map<String, Object> options, List<Map<String, Object>> rows) {
        List<String> columns = null;
        // options is never null (we default to empty map) so use it directly
        if (options.containsKey("columns") && options.get("columns") instanceof List) {
            columns = new ArrayList<>();
            for (Object c : (List<?>) options.get("columns")) {
                columns.add(c.toString());
            }
        }
        if (columns == null) {
            columns = new ArrayList<>();
            if (!rows.isEmpty()) {
                // preserve insertion order from the map
                columns.addAll(rows.getFirst().keySet());
            }
        }

        return columns;
    }

    @SneakyThrows
    private static HtmlBlock userProvidedTemplate(List<String> columns, List<Map<String, Object>> rows,
                                                  String dataSourceName, String mdName, String templateContent) {
        Path tempDir = Files.createTempDirectory("jte-templates");
        DirectoryCodeResolver codeResolver = new DirectoryCodeResolver(tempDir);
        TemplateEngine templateEngine = TemplateEngine.create(codeResolver, ContentType.Html);

        String templateName = "user-template.jte";
        Path templatePath = tempDir.resolve(templateName);

        Files.writeString(templatePath, templateContent); // Write the template content to the file

        Map<String, Object> params = new HashMap<>();
        params.put("columns", columns);
        params.put("rows", rows);
        params.put("datasource", dataSourceName);
        params.put("markdownFile", mdName);

        StringOutput output = new StringOutput();
        templateEngine.render(templateName, params, output);
        var html = new HtmlBlock();
        html.setLiteral(output.toString());
        return html;
    }

    private static List<Map<String, Object>> buildDataRows(ConfigService.DataSourceConfig dsConfig,
                                                           int limit, String query) {
        List<Map<String, Object>> rows;
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName(dsConfig.driverClassName());
        ds.setUrl(dsConfig.url());
        ds.setUsername(dsConfig.username());
        ds.setPassword(dsConfig.password());

        var jdbcTemplate = new JdbcTemplate(ds);
        if (limit > 0) {
            jdbcTemplate.setMaxRows(limit);
        }
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(jdbcTemplate);

        rows = named.queryForList(query, new MapSqlParameterSource());
        return rows;
    }

    private static HtmlBlock htmlFallbackTable(String datasourceName, List<String> columns, List<Map<String,
            Object>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"data-block\">\n");
        sb.append("<table class=\"data-block-table\">\n");
        // header
        sb.append("<thead><tr>");
        sb.append(String.format("<th colspan=%d>%s</th>", columns.size(), datasourceName));
        sb.append("</tr><tr>");
        for (String col : columns) {
            sb.append("<th>").append(escapeHtml(col)).append("</th>");
        }
        sb.append("</tr></thead>\n");
        // body
        sb.append("<tbody>\n");
        for (Map<String, Object> row : rows) {
            sb.append("<tr>");
            for (String col : columns) {
                Object v = row.get(col);
                sb.append("<td>").append(escapeHtml(v == null ? "(null)" : v.toString())).append("</td>");
            }
            sb.append("</tr>\n");
        }
        sb.append("</tbody>\n");
        sb.append("</table>\n");
        sb.append("</div>\n");

        var html = new HtmlBlock();
        html.setLiteral(sb.toString());
        return html;
    }

    private static String escapeHtml(String in) {
        if (in == null) return "";
        StringBuilder out = new StringBuilder(Math.max(16, in.length()));
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#x27;");
                case '/' -> out.append("&#x2F;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}