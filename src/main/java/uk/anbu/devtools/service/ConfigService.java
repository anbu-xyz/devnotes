package uk.anbu.devtools.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
        File dataSourceFile = new File(markdownDirectory, "config/datasource.json");
        if (dataSourceFile.exists()) {
            ObjectMapper mapper = new ObjectMapper();
            try {
                Map<String, DataSourceConfig> configs = mapper.readValue(dataSourceFile,
                        mapper.getTypeFactory().constructMapType(Map.class, String.class, DataSourceConfig.class));
                this.dataSources.putAll(configs);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load datasource configurations", e);
            }
        }
    }

    public DataSourceConfig getDataSourceConfig(String name) {
        return dataSources.get(name);
    }

    public record DataSourceConfig(String url, String username, String password, String driverClassName) {}
}