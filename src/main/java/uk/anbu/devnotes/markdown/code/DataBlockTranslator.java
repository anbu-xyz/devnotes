package uk.anbu.devnotes.markdown.code;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import gg.jte.resolve.DirectoryCodeResolver;
import j2html.tags.ContainerTag;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.Node;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import uk.anbu.devnotes.markdown.code.datablock.YamlCodeblockConfig;
import uk.anbu.devnotes.service.ConfigService;
import uk.anbu.devnotes.service.DatasourceConfigResolver;
import uk.anbu.devnotes.types.MarkdownFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static j2html.TagCreator.*;

@Slf4j
@RequiredArgsConstructor
public class DataBlockTranslator {
    private final DatasourceConfigResolver dataSourceConfigResolver;
    private final ConfigService configService;

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
        var allColumns = new ArrayList<>(rows.isEmpty() ? List.of() : rows.get(0).keySet());
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

    private static List<LinkedHashMap<String, Object>> buildDataRows(ConfigService.DataSourceConfig dsConfig,
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

        var rows = named.queryForStream(config.getQuery(), new MapSqlParameterSource(),
                (rs, rowNum) -> rowAsMap(config, rs));
        return rows.toList();
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
                Object v = row.get(col);
                boolean dataIsNumber = false;
                var dataCellText = v == null ? "(null)" : v.toString();
                if (v instanceof Number) {
                    dataIsNumber = true;
                }
                if (v instanceof java.math.BigDecimal || v instanceof Double || v instanceof Float) {
                    dataCellText = String.format("%,.2f", v);
                }
                var dataCell = td().withText(dataCellText);
                if (dataIsNumber) {
                    dataCell.withClass("data-is-number");
                }
                rowTag.with(dataCell);
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

        ContainerTag<?> wrapper = div().withClass("data-block").with(tableTag.with(theadTag, tbodyTag));
        return toHtmlBlock(wrapper);
    }

    private static HtmlBlock toHtmlBlock(ContainerTag<?> tag) {
        var html = new HtmlBlock();
        html.setLiteral(tag.render());
        return html;
    }
}
