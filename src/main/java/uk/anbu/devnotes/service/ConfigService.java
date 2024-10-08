package uk.anbu.devnotes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Data
@Slf4j
public class ConfigService {

    @Value("${devnotes.docsDirectory}")
    private String docsDirectory;
    @Value("${devnotes.sshKeyFile}")
    private String sshKeyFile;
    @Value("${devnotes.sql.maxRows:1000}")
    private int sqlMaxRows;
    private Map<String, DataSourceConfig> dataSources;

    public ConfigService() {
        this.dataSources = new HashMap<>();
    }

    @PostConstruct
    public void init() {
        if (docsDirectory == null || docsDirectory.isEmpty()) {
            docsDirectory = System.getProperty("user.home") + File.separator + "docs" + File.separator;
            log.warn("documents directory not set, using default: {}", docsDirectory);
        }
        log.info("Document root directory {}", docsDirectory);
        loadDataSourceConfigs();
    }

    private void loadDataSourceConfigs() {
        File dataSourceFile = new File(docsDirectory, "config/datasource.yaml");
        if (dataSourceFile.exists()) {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            try {
                Map<String, DataSourceConfig> configs = mapper.readValue(dataSourceFile,
                        mapper.getTypeFactory().constructMapType(Map.class, String.class, DataSourceConfig.class));
                var x = configs.entrySet().stream()
                        .map(e -> new DataSourceConfig(e.getKey(), e.getValue().url(), e.getValue().username(),
                                e.getValue().password(), e.getValue().driverClassName()))
                        .collect(Collectors.toMap(DataSourceConfig::name, e -> e));
                this.dataSources.putAll(x);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load datasource configurations", e);
            }
        }
    }

    public void updateDataSources(Map<String, String> newConfigs) {
        Map<String, DataSourceConfig> updatedDataSources = new HashMap<>(dataSources);

        for (Map.Entry<String, String> entry : newConfigs.entrySet()) {
            String[] parts = entry.getKey().split("\\.");
            if (parts.length == 2 && parts[0].startsWith("datasources[") && parts[0].endsWith("]")) {
                String dataSourceName = parts[0].substring(12, parts[0].length() - 1);
                String property = parts[1];
                DataSourceConfig config = updatedDataSources.getOrDefault(dataSourceName, new DataSourceConfig(dataSourceName, "", "", "", ""));

                config = switch (property) {
                    case "url" -> new DataSourceConfig(dataSourceName, entry.getValue(), config.username(), config.password(), config.driverClassName());
                    case "username" -> new DataSourceConfig(dataSourceName, config.url(), entry.getValue(), config.password(), config.driverClassName());
                    case "password" -> !entry.getValue().equals("********") ? new DataSourceConfig(dataSourceName, config.url(), config.username(), entry.getValue(), config.driverClassName()) : config;
                    case "driverClassName" -> new DataSourceConfig(dataSourceName, config.url(), config.username(), config.password(), entry.getValue());
                    default -> config;
                };

                updatedDataSources.put(dataSourceName, config);
            }
        }

        this.dataSources = updatedDataSources;
    }

    public void saveAndReloadConfig() {
        saveDataSourceConfigs();
        loadDataSourceConfigs();
    }

    private void saveDataSourceConfigs() {
        File dataSourceFile = new File(docsDirectory, "config/datasource.yaml");
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {
            mapper.writeValue(dataSourceFile, dataSources);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save datasource configurations", e);
        }
    }

    public DataSourceConfig getDataSourceConfig(String name) {
        return dataSources.get(name);
    }

    public Optional<String> getSshKey() {
        if (sshKeyFile == null || sshKeyFile.isEmpty() || sshKeyFile.isBlank()) {
            return Optional.empty();
        } else {
            return Optional.of(sshKeyFile);
        }
    }
}