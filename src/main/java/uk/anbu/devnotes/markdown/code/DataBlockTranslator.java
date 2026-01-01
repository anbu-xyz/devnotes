package uk.anbu.devnotes.markdown.code;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import gg.jte.resolve.DirectoryCodeResolver;
import j2html.tags.ContainerTag;
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
import java.util.*;

import static j2html.TagCreator.*;

@Slf4j
public class DataBlockTranslator {
    private final DatasourceConfigResolver dataSourceConfigResolver;

    public DataBlockTranslator(DatasourceConfigResolver dataSourceConfigResolver) {
        this.dataSourceConfigResolver = dataSourceConfigResolver;
    }

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
        int limit = options == null ? 0 : options.getRowLimit();
        List<Map<String, Object>> rows;
        try {
            rows = buildDataRows(dsConfig, limit, query);
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
        var columns = readColumnsData(options, rows);
        Boolean dontCombine = options == null ? null : options.getDontCombineSingleColumn();

        if (columns.size() == 1 && (dontCombine == null || !dontCombine)) {
            return Optional.of(combineIfSingleColumn(columns.getFirst(), rows));
        }

        // Check for output-template
        String templateContent = null;
        String outputTemplateType = null;
        if (config.getOutput() != null) {
            outputTemplateType = config.getOutput().getTemplateType();
            templateContent = config.getOutput().getTemplate();
        }

        if (templateContent != null && "jte".equalsIgnoreCase(outputTemplateType)) {
            try {
                var rendered = userProvidedTemplate(columns, rows, dataSourceName, mdName, templateContent);
                return Optional.of(rendered);
            } catch (Exception e) {
                log.error("Error rendering data block template", e);
                ContainerTag<?> err = div()
                        .withText("Error rendering template for data block ('" + mdName + "'): " + e.getMessage());
                return Optional.of(toHtmlBlock(err));
            }
        }

        String header = config.getHeader();
        // header might be a top-level property in YAML
        return Optional.of(htmlFallbackTable(header == null ? "" : header, columns, rows));
    }

    private static HtmlBlock combineIfSingleColumn(String firstColumnName, List<Map<String, Object>> rows) {
        List<String> values = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object v = row.get(firstColumnName);
            values.add(v == null ? "(null)" : v.toString());
        }

        ContainerTag<?> tbl = table().withClass("data-block-combined").with(
                tbody().with(
                        tr().with(
                                td().withClass("column-name").withText(firstColumnName),
                                td().withText(String.join(", ", values))
                        )
                )
        );

        return toHtmlBlock(tbl);
    }

    private static void cleanNullColumns(List<Map<String, Object>> rows) {
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

    private static List<String> readColumnsData(YamlCodeblockConfig.SqlOptions options, List<Map<String, Object>> rows) {
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
        if (options != null && options.getColumnsToExclude() != null && !options.getColumnsToExclude().isEmpty()) {
            columns.removeAll(options.getColumnsToExclude());
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
        var named = new NamedParameterJdbcTemplate(jdbcTemplate);

        rows = named.queryForList(query, new MapSqlParameterSource());
        return rows;
    }

    private static HtmlBlock htmlFallbackTable(String heading, List<String> columns,
                                               List<Map<String, Object>> rows) {
        var tableTag = table().withClass("data-block-table");

        // thead
        ContainerTag<?> theadTag = thead();
        if (heading != null && !heading.isEmpty()) {
            theadTag.with(tr().with(th().attr("colspan", String.valueOf(Math.max(1, columns.size()))).withText(heading)));
        }
        ContainerTag<?> headerRow = tr();
        for (String col : columns) {
            headerRow.with(th().withText(col));
        }
        theadTag.with(headerRow);

        // tbody
        ContainerTag<?> tbodyTag = tbody();
        for (Map<String, Object> row : rows) {
            ContainerTag<?> rowTag = tr();
            for (String col : columns) {
                Object v = row.get(col);
                rowTag.with(td().withText(v == null ? "(null)" : v.toString()));
            }
            tbodyTag.with(rowTag);
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
