package uk.anbu.devnotes.markdown.code;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import gg.jte.resolve.DirectoryCodeResolver;
import j2html.tags.ContainerTag;
import j2html.tags.specialized.TdTag;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.Node;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import uk.anbu.devnotes.markdown.code.datablock.ParameterRegistry;
import uk.anbu.devnotes.markdown.code.datablock.YamlCodeblockConfig;
import uk.anbu.devnotes.service.ConfigService;
import uk.anbu.devnotes.service.DatasourceConfigResolver;
import uk.anbu.devnotes.types.MarkdownFile;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Blob;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

import static j2html.TagCreator.*;

@Slf4j
@RequiredArgsConstructor
public class DataBlockTranslator {
    private final DatasourceConfigResolver dataSourceConfigResolver;
    private final ConfigService configService;
    private final ParameterRegistry parameterRegistry;

    @SneakyThrows
    public Optional<Node> renderDataBlock(String dataConfig, MarkdownFile markdownFile) {
        String mdName = markdownFile == null ? "" : markdownFile.fileName();

        // Parse YAML config
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        YamlCodeblockConfig config;
        try {
            config = yamlMapper.readValue(dataConfig, YamlCodeblockConfig.class);
        } catch (Exception e) {
            ContainerTag<?> err = div().withText("Error parsing data block YAML (" + mdName + "): " + e.getMessage());
            return Optional.of(toHtmlBlock(err));
        }

        // Resolve datasource
        String source = config.getSource();
        if (source == null || source.isEmpty()) {
            ContainerTag<?> err = div().withText("Error: 'source' not specified in data block (" + mdName + ").");
            return Optional.of(toHtmlBlock(err));
        }

        // allow source strings like 'database/datasourceName' or just the name
        String dataSourceName = source.contains("/") ? source.substring(source.lastIndexOf('/') + 1) : source;

        ConfigService.DataSourceConfig dsConfig = null;
        if (dataSourceConfigResolver != null) {
            dsConfig = dataSourceConfigResolver.resolve(dataSourceName);
        }

        if (dsConfig == null) {
            ContainerTag<?> err = div()
                    .withText("Error: DataSource '" + dataSourceName + "' not configured (" + mdName + ").");
            return Optional.of(toHtmlBlock(err));
        }

        // Extract query
        String query = config.getQuery();
        if (query == null || query.isEmpty()) {
            ContainerTag<?> err = div().withText("Error: 'query' not specified in data block (" + mdName + ").");
            return Optional.of(toHtmlBlock(err));
        }

        // Extract options in a type-safe way
        var options = config.getOptions();
        int limit = configService.getSqlMaxRows() == 0 ? 100 : configService.getSqlMaxRows();
        limit = options == null || options.getRowLimit() == 0 ? limit : options.getRowLimit();
        boolean maxRowsReached = false;
        List<LinkedHashMap<String, Object>> rows;
        try {
            rows = buildDataRows(dsConfig, config, limit + 1);
            if (rows.size() > limit) {
                rows = rows.subList(0, limit);
                maxRowsReached = true;
            }
            cleanNullColumns(rows);
            if (rows.isEmpty() || rows.getFirst().isEmpty()) {
                return Optional.empty();
            }
        } catch (Exception e) {
            ContainerTag<?> err = div().withText("Error executing query against datasource '" + dataSourceName
                    + "' (" + mdName + "): " + e.getMessage());
            return Optional.of(toHtmlBlock(err));
        }

        // If columns not provided, infer from first row
        var columns = readColumnsData(config, rows);

        if (columns.size() == 1 && config.isCombineSingleColumn()) {
            return Optional.of(combineIfSingleColumn(rows, maxRowsReached));
        }

        // Check for output-template
        String templateContent = null;
        String outputTemplateType = null;
        if (config.getOutput() != null) {
            outputTemplateType = config.getOutput().getTemplateType();
            templateContent = config.getOutput().getTemplate();
        }

        if (templateContent != null && "jte".equalsIgnoreCase(outputTemplateType)) {
            return buildFromTemplate(columns, rows, dataSourceName, mdName, templateContent);
        }

        return Optional.of(htmlFallbackTable(config, rows, maxRowsReached));
    }

    private static Optional<Node> buildFromTemplate(List<String> columns,
                                                    List<LinkedHashMap<String, Object>> rows,
                                                    String dataSourceName, String mdName, String templateContent) {
        try {
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
            return Optional.of(html);
        } catch (Exception e) {
            log.error("Error rendering data block template", e);
            ContainerTag<?> err = div()
                    .withText("Error rendering template for data block ('" + mdName + "'): " + e.getMessage());
            return Optional.of(toHtmlBlock(err));
        }
    }

    private static HtmlBlock combineIfSingleColumn(List<LinkedHashMap<String, Object>> rows,
                                                   boolean maxRowsReached) {
        List<String> values = new ArrayList<>();
        String firstColumnName = rows.isEmpty() ? "" : rows.getFirst().keySet().iterator().next();
        for (Map<String, Object> row : rows) {
            values.add(row.get(firstColumnName) == null ? "(null)" : row.get(firstColumnName).toString());
        }

        ContainerTag<?> tbl = table().withClass("data-block-combined").with(
                tbody().with(
                        tr().with(
                                th().withClass("column-name").withText(firstColumnName),
                                td().with(
                                        text(String.join(", ", values)),
                                        span().withClass("data-block-status-span")
                                                .withText(
                                                        maxRowsReached ? String.format(" ... max limit reached (%d)", rows.size()) : ""
                                                )
                                )
                        )
                )
        );

        return toHtmlBlock(tbl);
    }

    private static void cleanNullColumns(List<LinkedHashMap<String, Object>> rows) {
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

    private static List<String> readColumnsData(YamlCodeblockConfig config, List<LinkedHashMap<String, Object>> rows) {
        var options = config.getOptions();
        List<String> columns = null;
        if (options != null && options.getColumns() != null && !options.getColumns().isEmpty()) {
            columns = new ArrayList<>(options.getColumns());
        }
        if (columns == null) {
            columns = new ArrayList<>();
            if (!rows.isEmpty()) {
                // preserve insertion order from the map
                columns.addAll(rows.getFirst().keySet());
            }
        }
        if (config.getColumnsToExclude() != null && !config.getColumnsToExclude().isEmpty()) {
            columns.removeAll(config.getColumnsToExclude());
        }

        return columns;
    }

    private List<LinkedHashMap<String, Object>> buildDataRows(ConfigService.DataSourceConfig dsConfig,
                                                           YamlCodeblockConfig config, int limit) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName(dsConfig.driverClassName());
        ds.setUrl(dsConfig.url());
        ds.setUsername(dsConfig.username());
        ds.setPassword(dsConfig.password());

        var jdbcTemplate = new JdbcTemplate(ds);
        if (limit > 0) {
            jdbcTemplate.setMaxRows(limit);
        }
        var named = new NamedParameterJdbcTemplate(jdbcTemplate);

        MapSqlParameterSource paramSource = buildParameterSource(config);

        var rows = named.queryForStream(config.getQuery(), paramSource,
                (rs, rowNum) -> rowAsMap(config, rs));
        return rows.toList();
    }

    private MapSqlParameterSource buildParameterSource(YamlCodeblockConfig config) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        // First, add YAML-local parameters (they take precedence)
        if (config != null && config.getParameters() != null && !config.getParameters().isEmpty()) {
            for (Map.Entry<String, YamlCodeblockConfig.SqlParameter> e : config.getParameters().entrySet()) {
                String name = e.getKey();
                YamlCodeblockConfig.SqlParameter p = e.getValue();
                if (p == null) {
                    continue;
                }
                Object rawValue = p.getValue();
                String type = p.getType();
                try {
                    Object coerced = coerceParameterValue(rawValue, type);
                    int sqlType = mapTypeToSqlType(type);
                    if (sqlType != Types.OTHER) {
                        params.addValue(name, coerced, sqlType);
                    } else {
                        params.addValue(name, coerced);
                    }
                } catch (Exception ex) {
                    // if coercion failed, fall back to string representation
                    params.addValue(name, rawValue == null ? null : rawValue.toString());
                }
            }
        }

        // Merge shared parameters from the registry
        var shared = parameterRegistry == null ? Map.<String, Object>of() : parameterRegistry.getAll();
        for (Map.Entry<String, Object> se : shared.entrySet()) {
            String name = se.getKey();
            Object rawValue = se.getValue();
            try {
                // Allow registry (shared) values to override YAML-local parameters
                params.addValue(name, rawValue);
            } catch (Exception ex) {
                params.addValue(name, rawValue == null ? null : rawValue.toString());
            }
        }

        return params;
    }

    private static Object coerceParameterValue(Object rawValue, String type) {
        if (rawValue == null) return null;
        if (type == null || type.isBlank()) return rawValue;
        String t = type.trim().toLowerCase(Locale.ROOT);
        if (rawValue instanceof Number) {
            var n = (Number) rawValue;
            switch (t) {
                case "int":
                case "integer":
                case "short":
                case "tinyint":
                    return n.intValue();
                case "long":
                case "bigint":
                    return n.longValue();
                case "double":
                case "float":
                case "real":
                    return n.doubleValue();
                case "bigdecimal":
                case "decimal":
                    return BigDecimal.valueOf(n.doubleValue());
                default:
                    return rawValue;
            }
        }
        String s = rawValue.toString();
        switch (t) {
            case "int":
            case "integer":
            case "short":
            case "tinyint":
                return Integer.parseInt(s);
            case "long":
            case "bigint":
                return Long.parseLong(s);
            case "double":
            case "float":
            case "real":
                return Double.parseDouble(s);
            case "bigdecimal":
            case "decimal":
                return new BigDecimal(s);
            case "boolean":
            case "bool":
                return Boolean.parseBoolean(s);
            case "string":
            case "varchar":
            case "text":
                return s;
            case "date":
                try {
                    LocalDate ld = LocalDate.parse(s);
                    return Date.valueOf(ld);
                } catch (DateTimeParseException ex) {
                    throw new IllegalArgumentException("Invalid date format for parameter: " + s);
                }
            case "timestamp":
            case "datetime":
                try {
                    // Try parse as LocalDateTime first, then LocalDate
                    LocalDateTime ldt = LocalDateTime.parse(s);
                    return Timestamp.valueOf(ldt);
                } catch (DateTimeParseException ex) {
                    try {
                        LocalDate ld2 = LocalDate.parse(s);
                        return Timestamp.valueOf(ld2.atStartOfDay());
                    } catch (DateTimeParseException ex2) {
                        throw new IllegalArgumentException("Invalid timestamp format for parameter: " + s);
                    }
                }
            default:
                return rawValue;
        }
    }

    private static int mapTypeToSqlType(String type) {
        if (type == null || type.isBlank()) return Types.OTHER;
        String t = type.trim().toLowerCase(Locale.ROOT);
        return switch (t) {
            case "int", "integer", "short", "tinyint" -> Types.INTEGER;
            case "long", "bigint" -> Types.BIGINT;
            case "double", "float", "real" -> Types.DOUBLE;
            case "bigdecimal", "decimal" -> Types.DECIMAL;
            case "boolean", "bool" -> Types.BOOLEAN;
            case "string", "varchar", "text" -> Types.VARCHAR;
            case "date" -> Types.DATE;
            case "timestamp", "datetime" -> Types.TIMESTAMP;
            case "blob" -> Types.BLOB;
            default -> Types.OTHER;
        };
    }

    private static LinkedHashMap<String, Object> rowAsMap(YamlCodeblockConfig config,
                                                          ResultSet rs) throws SQLException {
        var map = new LinkedHashMap<String, Object>();
        var meta = rs.getMetaData();
        var columnsToExclude = config.getColumnsToExclude().stream()
                .map(String::toUpperCase)
                .toList();
        var columnsToSelect = config.getOptions() != null && config.getOptions().getColumns() != null
                ? config.getOptions().getColumns().stream().map(String::toUpperCase).toList()
                : null;
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String columnName = meta.getColumnLabel(i);
            if (columnsToExclude.contains(columnName.toUpperCase())) {
                continue;
            }
            if (columnsToSelect != null && !columnsToSelect.contains(columnName.toUpperCase())) {
                continue;
            }

            Object value = rs.getObject(i);
            map.put(columnName, value);
        }
        return map;
    }

    private static HtmlBlock htmlFallbackTable(YamlCodeblockConfig config,
                                               List<LinkedHashMap<String, Object>> rows,
                                               boolean maxRowsReached) {
        if (config.isTranspose()) {
            return transposedViewTable(config, rows, maxRowsReached);
        }
        var tableTag = table().withClass("data-block-table");

        var columns = rows.getFirst().keySet();
        // thead
        var header = config.getHeader() == null ? "" : config.getHeader();
        ContainerTag<?> theadTag = thead();
        if (!header.isEmpty()) {
            theadTag.with(tr().with(
                    th()
                            .withClass("data-block-header-cell")
                            .attr("colspan", String.valueOf(Math.max(1, columns.size()))).withText(header))
            );
        }
        ContainerTag<?> headerRow = tr();
        for (String col : columns) {
            headerRow.with(th().withText(col));
        }
        theadTag.with(headerRow);

        // tbody
        ContainerTag<?> tbodyTag = tbody();
        for (Map<String, Object> row : rows) {
            ContainerTag<?> rowTag = tr().withClass("data-block-data-row");
            for (String col : columns) {
                rowTag.with(createTdTag(col, row));
            }
            tbodyTag.with(rowTag);
        }

        if (maxRowsReached) {
            tbodyTag.with(tr()
                    .withClass("data-block-status-row")
                    .with(
                            td().withClass("data-block-status-cell")
                                    .attr("colspan", String.valueOf(Math.max(1, columns.size())))
                                    .withText(String.format("... max limit reached (%d rows)", rows.size()))
                    ));
        } else {
            if (!config.isHideRowCount() && rows.size() >= config.getHideRowCountWhenLessThan()) {
                tbodyTag.with(tr().withClass("data-block-status-row")
                        .with(
                                td().withClass("data-block-status-cell")
                                        .attr("colspan", String.valueOf(Math.max(1, columns.size())))
                                        .withText(String.format("Total rows: %d", rows.size()))
                        ));
            }
        }

        ContainerTag<?> wrapper = div().withClass("data-block")
                .attr("style", "position:relative;")
                .with(
                        div().withClass("data-block-controls")
                                .attr("style", "position:absolute; top:8px; right:8px; z-index:10;")
                                .with(
                                        button().withClass("data-block-more-btn")
                                                .attr("type", "button")
                                                .attr("onclick", "const m=this.nextElementSibling; m.style.display = m.style.display==='block' ? 'none' : 'block';")
                                                .withText("⋮"),
                                        ul().withClass("data-block-menu")
                                                .attr("style", "display:none; position:absolute; right:0; top:28px; background:#fff; border:1px solid #ccc; padding:4px; margin:0; list-style:none;")
                                                .with(
                                                        li().with(a("Source"))
                                                )
                                ),
                        tableTag.with(theadTag, tbodyTag)
                );
        return toHtmlBlock(wrapper);
    }

    private static HtmlBlock transposedViewTable(YamlCodeblockConfig config,
                                                List<LinkedHashMap<String, Object>> rows, boolean maxRowsReached) {
        var tableTag = table().withClass("data-block-table transpose");
        var columns = rows.getFirst().keySet();
        for (String col : columns) {
            ContainerTag<?> rowTag = tr().withClass("data-block-data-row");
            rowTag.with(th().withText(col));
            for (Map<String, Object> row : rows) {
                rowTag.with(createTdTag(col, row));
            }
            tableTag.with(rowTag);
        }
        if (maxRowsReached) {
            tableTag.with(tr()
                    .withClass("data-block-status-row")
                    .with(
                            td().withClass("data-block-status-cell")
                                    .attr("colspan", String.valueOf(Math.max(1, rows.size() + 1)))
                                    .withText(String.format("... max limit reached (%d rows)", rows.size()))
                    ));
        } else {
            if (!config.isHideRowCount() && rows.size() >= config.getHideRowCountWhenLessThan()) {
                tableTag.with(tr().withClass("data-block-status-row")
                        .with(
                                td().withClass("data-block-status-cell")
                                        .attr("colspan", String.valueOf(Math.max(1, rows.size() + 1)))
                                        .withText(String.format("Total rows: %d", rows.size()))
                        ));
            }
        }
        return toHtmlBlock(tableTag);
    }

    private static TdTag createTdTag(String col, Map<String, Object> row) {
        Object v = row.get(col);
        boolean dataIsNumber = false;
        var dataCellText = v == null ? "(null)" : v.toString();
        if (v instanceof Number) {
            dataIsNumber = true;
        }
        if (v instanceof java.math.BigDecimal || v instanceof Double || v instanceof Float) {
            dataCellText = String.format("%,.2f", ((Number) v).doubleValue());
        }
        if (v instanceof Blob blob) {
            try {
                dataCellText = String.format("<blob, %d bytes>", blob.length());
            } catch (SQLException e) {
                dataCellText = "<blob>";
            }
        }
        var dataCell = td().withText(dataCellText);
        if (dataIsNumber) {
            dataCell.withClass("data-block-number");
        }
        return dataCell;
    }

    private static HtmlBlock toHtmlBlock(ContainerTag<?> tag) {
        var html = new HtmlBlock();
        html.setLiteral(tag.render());
        return html;
    }
}
