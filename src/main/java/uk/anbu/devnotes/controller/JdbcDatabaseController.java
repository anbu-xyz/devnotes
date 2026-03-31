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

        var effectiveTablePattern = tablePattern != null && !tablePattern.isBlank() ? tablePattern : "%";

        try (var conn = DriverManager.getConnection(dbConfig.url(), dbConfig.username(), dbConfig.password())) {
            var dbMetaData    = conn.getMetaData();
            var dbProductName = dbMetaData.getDatabaseProductName();
            var typeFieldName = JdbcTypeMapper.resolveTypeFieldName(dbProductName);
            return fetchAllTables(dbMetaData, configName, schemaPattern, effectiveTablePattern,
                dbProductName, typeFieldName);
        }
    }

    private Map<String, DatabaseMetadataConfig> fetchAllTables(
            DatabaseMetaData dbMetaData,
            String configName,
            String schemaPattern,
            String tablePattern,
            String dbProductName,
            String typeFieldName) throws SQLException {

        var result = new LinkedHashMap<String, DatabaseMetadataConfig>();
        try (var rs = dbMetaData.getTables(null, schemaPattern, tablePattern, new String[]{"TABLE"})) {
            while (rs.next()) {
                var tableName = rs.getString("TABLE_NAME"); // preserve original case for subsequent JDBC calls
                var schema    = rs.getString("TABLE_SCHEM");
                result.put(tableName.toLowerCase(), buildTableConfig(dbMetaData, schema, tableName, configName, dbProductName, typeFieldName));
            }
        }
        return result;
    }

    private DatabaseMetadataConfig buildTableConfig(
            DatabaseMetaData dbMetaData,
            String schema,
            String tableName,
            String configName,
            String dbProductName,
            String typeFieldName) throws SQLException {

        var config = new DatabaseMetadataConfig();
        config.setTable(buildTableInfo(tableName.toLowerCase(), configName));
        config.setColumns(fetchColumns(dbMetaData, schema, tableName, dbProductName, typeFieldName));
        return config;
    }

    private DatabaseMetadataConfig.TableInfo buildTableInfo(String tableName, String configName) {
        var tableInfo = new DatabaseMetadataConfig.TableInfo();
        tableInfo.setName(tableName);
        tableInfo.setDatasource(configName);
        return tableInfo;
    }

    private Map<String, DatabaseMetadataConfig.ColumnConfig> fetchColumns(
            DatabaseMetaData dbMetaData,
            String schema,
            String tableName,
            String dbProductName,
            String typeFieldName) throws SQLException {

        var columns = new LinkedHashMap<String, DatabaseMetadataConfig.ColumnConfig>();
        try (var columnRs = dbMetaData.getColumns(null, schema, tableName, "%")) {
            while (columnRs.next()) {
                var columnName = columnRs.getString("COLUMN_NAME").toLowerCase();
                columns.put(columnName, buildColumnConfig(columnRs, dbProductName, typeFieldName));
            }
        }
        return columns;
    }

    private DatabaseMetadataConfig.ColumnConfig buildColumnConfig(
            ResultSet columnRs,
            String dbProductName,
            String typeFieldName) throws SQLException {

        var typeName  = columnRs.getString("TYPE_NAME");
        var size      = columnRs.getInt("COLUMN_SIZE");
        var digits    = columnRs.getInt("DECIMAL_DIGITS");
        var typeStr   = JdbcTypeMapper.formatType(dbProductName, typeName, size, digits);

        var colConfig = new DatabaseMetadataConfig.ColumnConfig();
        switch (typeFieldName) {
            case "h2-type"     -> colConfig.setH2Type(typeStr);
            case "oracle-type" -> colConfig.setOracleType(typeStr);
            default            -> colConfig.setDbType(typeStr);
        }
        colConfig.setJavaType(JdbcTypeMapper.toJavaType(typeName));
        return colConfig;
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