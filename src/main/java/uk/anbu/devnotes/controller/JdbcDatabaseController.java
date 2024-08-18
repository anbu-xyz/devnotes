package uk.anbu.devnotes.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.anbu.devnotes.service.ConfigService;

import java.io.File;
import java.io.IOException;
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
        templateEngine.render("database.jte", model, output);
        return ResponseEntity.ok(output.toString());
    }

    @PostMapping("/database/fetch-metadata")
    public ResponseEntity<String> fetchDatabaseMetadata(@RequestParam String configName, @RequestParam String targetName) {
        ConfigService.DataSourceConfig config = configService.getDataSourceConfig(configName);
        if (config == null) {
            return ResponseEntity.badRequest().body("Invalid datasource configuration name");
        }

        try {
            Map<String, Object> metadata = fetchMetadata(config);
            saveMetadataToYaml(metadata, targetName);
            return ResponseEntity.ok("Metadata fetched and saved successfully");
        } catch (SQLException | IOException e) {
            log.error("Error fetching or saving database metadata", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }

    private Map<String, Object> fetchMetadata(ConfigService.DataSourceConfig config) throws SQLException {
        Map<String, Object> metadata = new HashMap<>();
        try (Connection conn = DriverManager.getConnection(config.url(), config.username(), config.password())) {
            DatabaseMetaData dbMetaData = conn.getMetaData();
            metadata.put("database_product_name", dbMetaData.getDatabaseProductName());
            metadata.put("database_product_version", dbMetaData.getDatabaseProductVersion());

            Map<String, List<Map<String, Object>>> tables = new HashMap<>();
            try (ResultSet rs = dbMetaData.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    List<Map<String, Object>> columns = new ArrayList<>();
                    try (ResultSet columnRs = dbMetaData.getColumns(null, null, tableName, "%")) {
                        while (columnRs.next()) {
                            columns.add(Map.of(
                                    "name", columnRs.getString("COLUMN_NAME"),
                                    "type", columnRs.getString("TYPE_NAME"),
                                    "size", columnRs.getInt("COLUMN_SIZE"),
                                    "nullable", columnRs.getBoolean("NULLABLE")
                            ));
                        }
                    }
                    tables.put(tableName, columns);
                }
            }
            metadata.put("tables", tables);
        }
        return metadata;
    }

    private void saveMetadataToYaml(Map<String, Object> metadata, String targetName) throws IOException {
        File targetFile = new File(configService.getDocsDirectory(), "database/" + targetName + ".yaml");
        targetFile.getParentFile().mkdirs();
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        yamlMapper.writeValue(targetFile, metadata);
    }
}