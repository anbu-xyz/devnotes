package uk.anbu.devtools.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
@Data
public class ConfigService {

    private String markdownDirectory;
    private Map<String, DataSourceConfig> dataSources;

    public ConfigService() {
        this.markdownDirectory = "//wsl.localhost/Ubuntu/home/anbu/workspace/docs";
        this.dataSources = new HashMap<>();
    }

    @PostConstruct
    public void init() {
        loadDataSourceConfigs();
    }

    private void loadDataSourceConfigs() {
        File dataSourceFile = new File(markdownDirectory, "config/datasource.yaml");
        if (dataSourceFile.exists()) {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            try {
                Map<String, DataSourceConfig> configs = mapper.readValue(dataSourceFile,
                        mapper.getTypeFactory().constructMapType(Map.class, String.class, DataSourceConfig.class));
                this.dataSources.putAll(configs);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load datasource configurations", e);
            }
        }
    }

    public void updateDataSources(Map<String, String> newConfigs) {
        for (Map.Entry<String, String> entry : newConfigs.entrySet()) {
            String[] parts = entry.getKey().split("\\.");
            if (parts.length == 2) {
                String dataSourceName = parts[0].replace("datasources[", "").replace("]", "");
                String property = parts[1];
                DataSourceConfig config = dataSources.getOrDefault(dataSourceName, new DataSourceConfig("", "", "", ""));
                switch (property) {
                    case "url" -> config = new DataSourceConfig(entry.getValue(), config.username(), config.password(), config.driverClassName());
                    case "username" -> config = new DataSourceConfig(config.url(), entry.getValue(), config.password(), config.driverClassName());
                    case "password" -> {
                        if (!entry.getValue().equals("********")) {
                            config = new DataSourceConfig(config.url(), config.username(), entry.getValue(), config.driverClassName());
                        }
                    }
                    case "driverClassName" -> config = new DataSourceConfig(config.url(), config.username(), config.password(), entry.getValue());
                }
                dataSources.put(dataSourceName, config);
            }
        }
    }

    public void saveAndReloadConfig() {
        saveDataSourceConfigs();
        loadDataSourceConfigs();
    }

    private void saveDataSourceConfigs() {
        File dataSourceFile = new File(markdownDirectory, "config/datasource.yaml");
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

    public record DataSourceConfig(String url, String username, String password, String driverClassName) {}
}