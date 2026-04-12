package uk.anbu.devnotes.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devnotes.markdown.code.databasemetadata.DatabaseMetadataConfig;
import uk.anbu.devnotes.service.ConfigService;
import uk.anbu.devnotes.util.JdbcTypeMapper;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static j2html.TagCreator.div;

@RestController
@RequiredArgsConstructor
@Slf4j
public class DatabaseMetadataController {

    private final ConfigService configService;
    private final TemplateEngine templateEngine;

    @PostMapping(value = "/database-metadata/check", produces = MediaType.TEXT_HTML_VALUE)
    public String check(@RequestParam String datasource, @RequestParam String yamlContent) {
        // Parse YAML
        DatabaseMetadataConfig config;
        try {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            config = yamlMapper.readValue(yamlContent, DatabaseMetadataConfig.class);
        } catch (Exception e) {
            log.warn("Failed to parse database-metadata YAML for check", e);
            return errorDiv("Invalid database-metadata YAML: " + e.getMessage());
        }

        if (config.getTable() == null || config.getTable().getName() == null) {
            return errorDiv("YAML must contain a 'table.name' field.");
        }
        String tableName = config.getTable().getName();

        // Resolve datasource
        ConfigService.DataSourceConfig dsConfig = configService.getDataSourceConfig(datasource);
        if (dsConfig == null) {
            return errorDiv("Unknown datasource: '" + datasource + "'");
        }

        // Open JDBC connection
        try {
            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setUrl(dsConfig.url());
            ds.setUsername(dsConfig.username());
            ds.setPassword(dsConfig.password());

            try (Connection conn = ds.getConnection()) {
                DatabaseMetaData meta = conn.getMetaData();
                var dbProductName = meta.getDatabaseProductName();
                var typeFieldName = JdbcTypeMapper.resolveTypeFieldName(dbProductName);
                List<LiveColumn> liveColumns = fetchLiveColumns(meta, tableName);

                if (liveColumns == null) {
                    return warnDiv("Table '" + tableName + "' not found in datasource '" + datasource + "'.");
                }

                return renderDiffTemplate(tableName, config, liveColumns, typeFieldName);
            }
        } catch (Exception e) {
            log.error("JDBC error during database-metadata check for table '{}' on datasource '{}'",
                    tableName, datasource, e);
            return errorDiv("Connection error for datasource '" + datasource + "': " + e.getMessage());
        }
    }

    private List<LiveColumn> fetchLiveColumns(DatabaseMetaData meta, String tableName) throws Exception {
        boolean tableFound = false;
        try (ResultSet tables = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                if (tableName.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    tableFound = true;
                    break;
                }
            }
        }
        if (!tableFound) {
            return null;
        }

        List<LiveColumn> columns = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(null, null, tableName.toUpperCase(), "%")) {
            while (rs.next()) {
                var rawType = rs.getString("TYPE_NAME");
                columns.add(new LiveColumn(
                        rs.getString("COLUMN_NAME"),
                        rawType,
                        rawType + "(" + rs.getInt("COLUMN_SIZE") + ")"
                ));
            }
        }
        // Some databases (e.g. H2) use the exact case supplied; try original if nothing returned
        if (columns.isEmpty()) {
            try (ResultSet rs = meta.getColumns(null, null, tableName, "%")) {
                while (rs.next()) {
                    var rawType = rs.getString("TYPE_NAME");
                    columns.add(new LiveColumn(
                            rs.getString("COLUMN_NAME"),
                            rawType,
                            rawType + "(" + rs.getInt("COLUMN_SIZE") + ")"
                    ));
                }
            }
        }
        return columns;
    }

    private String renderDiffTemplate(String tableName,
                                      DatabaseMetadataConfig config,
                                      List<LiveColumn> liveColumns,
                                      String typeFieldName) {
        // Normalise column names to lowercase for case-insensitive comparison
        Map<String, String> liveByName = new LinkedHashMap<>();
        Map<String, String> rawTypeByName = new LinkedHashMap<>();
        for (LiveColumn c : liveColumns) {
            liveByName.put(c.name().toLowerCase(), c.typeSummary());
            rawTypeByName.put(c.name().toLowerCase(), c.rawTypeName());
        }

        Set<String> wikiNames = new TreeSet<>();
        if (config.getColumns() != null) {
            config.getColumns().keySet().forEach(k -> wikiNames.add(k.toLowerCase()));
        }
        Set<String> liveNames = new TreeSet<>(liveByName.keySet());

        Set<String> matched      = new TreeSet<>(liveNames); matched.retainAll(wikiNames);
        Set<String> undocumented = new TreeSet<>(liveNames); undocumented.removeAll(wikiNames);
        Set<String> ghosts       = new TreeSet<>(wikiNames); ghosts.removeAll(liveNames);

        // Build sorted Map<colName, typeSummary> for undocumented entries
        Map<String, String> undocumentedMap = new TreeMap<>();
        for (String col : undocumented) {
            undocumentedMap.put(col, liveByName.get(col));
        }

        // Build YAML snippet for undocumented columns (ready to paste into the columns: block)
        var yamlSb = new StringBuilder();
        for (String col : undocumented) {
            var javaType = JdbcTypeMapper.toJavaType(rawTypeByName.get(col));
            yamlSb.append("  ").append(col).append(":\n");
            yamlSb.append("    ").append(typeFieldName).append(": ").append(undocumentedMap.get(col)).append("\n");
            yamlSb.append("    java-type: ").append(javaType).append("\n");
        }
        var undocumentedYaml = yamlSb.isEmpty() ? "" : yamlSb.toString().stripTrailing();

        Map<String, Object> params = new HashMap<>();
        params.put("tableName", tableName);
        params.put("matched", matched);
        params.put("undocumented", undocumentedMap);
        params.put("ghosts", ghosts);
        params.put("undocumentedYaml", undocumentedYaml);

        TemplateOutput output = new StringOutput();
        templateEngine.render("database-metadata-diff.jte", params, output);
        return output.toString();
    }

    private static String errorDiv(String message) {
        return div().withClass("database-metadata-error").withText("❌ " + message).render();
    }

    private static String warnDiv(String message) {
        return div().withClass("db-meta-diff-undocumented").withText("⚠️ " + message).render();
    }

    private record LiveColumn(String name, String rawTypeName, String typeSummary) {}
}
