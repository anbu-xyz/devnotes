package uk.anbu.devnotes.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.anbu.devnotes.markdown.code.databasemetadata.DatabaseMetadataConfig;
import uk.anbu.devnotes.service.ConfigService;
import uk.anbu.devnotes.util.JdbcTypeMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.sql.*;
import java.util.*;

@RestController
@RequiredArgsConstructor
@Slf4j
public class JdbcDatabaseController {

    private final ConfigService configService;
    private final TemplateEngine templateEngine;

    @GetMapping("/database")
    public ResponseEntity<String> databasePage() {
        Map<String, Object> model = Map.of(
                "dataSources", configService.getDataSources().keySet()
        );
        TemplateOutput output = new StringOutput();
        templateEngine.render("tools/database.jte", model, output);
        return ResponseEntity.ok(output.toString());
    }

    @PostMapping("/database/fetch-metadata")
    public ResponseEntity<String> fetchDatabaseMetadata(
            @RequestParam String configName,
            @RequestParam String targetName,
            @RequestParam(required = false) String schemaPattern,
            @RequestParam(required = false) String tablePattern) {

        ConfigService.DataSourceConfig config = configService.getDataSourceConfig(configName);
        if (config == null) {
            return ResponseEntity.badRequest().body("Invalid datasource configuration name");
        }

        try {
            Map<String, DatabaseMetadataConfig> tables =
                    fetchMetadata(config, configName, schemaPattern, tablePattern);
            String filePath = saveAllTablesAsMarkdownFile(tables, targetName);

            StringBuilder summary = new StringBuilder();
            summary.append("Generated ").append(filePath)
                    .append(" with ").append(tables.size()).append(" table(s):\n");
            new TreeMap<>(tables).keySet().forEach(t -> summary.append("  ").append(t).append("\n"));

            return ResponseEntity.ok(summary.toString());
        } catch (SQLException | IOException e) {
            log.error("Error fetching or saving database metadata", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }

    private Map<String, DatabaseMetadataConfig> fetchMetadata(
            ConfigService.DataSourceConfig dbConfig,
            String configName,
            String schemaPattern,
            String tablePattern) throws SQLException {

        Map<String, DatabaseMetadataConfig> result = new LinkedHashMap<>();
        String effectiveTablePattern = tablePattern != null ? tablePattern : "%";

        try (Connection conn = DriverManager.getConnection(dbConfig.url(), dbConfig.username(), dbConfig.password())) {
            DatabaseMetaData dbMetaData = conn.getMetaData();
            String dbProductName = dbMetaData.getDatabaseProductName();
            String typeFieldName = JdbcTypeMapper.resolveTypeFieldName(dbProductName);

            try (ResultSet rs = dbMetaData.getTables(null, schemaPattern, effectiveTablePattern, new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String schema    = rs.getString("TABLE_SCHEM");

                    DatabaseMetadataConfig config = new DatabaseMetadataConfig();
                    DatabaseMetadataConfig.TableInfo tableInfo = new DatabaseMetadataConfig.TableInfo();
                    tableInfo.setName(tableName.toLowerCase());
                    tableInfo.setDatasource(configName);
                    config.setTable(tableInfo);

                    Map<String, DatabaseMetadataConfig.ColumnConfig> columns = new LinkedHashMap<>();
                    try (ResultSet columnRs = dbMetaData.getColumns(null, schema, tableName, "%")) {
                        while (columnRs.next()) {
                            String columnName = columnRs.getString("COLUMN_NAME").toLowerCase();
                            String typeName   = columnRs.getString("TYPE_NAME");
                            int    size       = columnRs.getInt("COLUMN_SIZE");
                            int    digits     = columnRs.getInt("DECIMAL_DIGITS");

                            DatabaseMetadataConfig.ColumnConfig colConfig = new DatabaseMetadataConfig.ColumnConfig();
                            String typeStr = JdbcTypeMapper.formatType(dbProductName, typeName, size, digits);

                            switch (typeFieldName) {
                                case "h2-type"     -> colConfig.setH2Type(typeStr);
                                case "oracle-type" -> colConfig.setOracleType(typeStr);
                                default            -> colConfig.setDbType(typeStr);
                            }

                            colConfig.setJavaType(JdbcTypeMapper.toJavaType(typeName));
                            columns.put(columnName, colConfig);
                        }
                    }

                    config.setColumns(columns);
                    result.put(tableName.toLowerCase(), config);
                }
            }
        }

        return result;
    }

    private String saveAllTablesAsMarkdownFile(Map<String, DatabaseMetadataConfig> tables,
                                               String targetName) throws IOException {
        File targetFile = new File(configService.getDocsDirectory(), "database/" + targetName + ".md");
        File parentDir  = targetFile.getParentFile();
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            log.error("Failed to create directory {}", parentDir.getAbsolutePath());
            throw new IOException("Failed to create directory: " + parentDir.getAbsolutePath());
        }

        ObjectMapper yamlMapper = new ObjectMapper(
                new YAMLFactory()
                        .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                        .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
        yamlMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, DatabaseMetadataConfig> entry : new TreeMap<>(tables).entrySet()) {
            String yamlBody = yamlMapper.writeValueAsString(entry.getValue());
            sb.append("```database-metadata\n");
            sb.append(yamlBody);
            sb.append("```\n\n");
        }

        String content = sb.toString().stripTrailing() + "\n";
        Files.writeString(targetFile.toPath(), content,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        return "database/" + targetName + ".md";
    }
}