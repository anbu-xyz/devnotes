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
public class ConfigServiceImpl implements ConfigService {

    @Value("${devnotes.docsDirectory}")
    private String docsDirectory;
    @Value("${devnotes.sshKeyFile}")
    private String sshKeyFile;
    @Value("${devnotes.chromeDriverLocation}")
    private String chromeDriverLocation;
    @Value("${devnotes.sql.maxRows:1000}")
    private int sqlMaxRows;
    private Map<String, DataSourceConfig> dataSources;

    private EncryptionService encryptionService;

    public ConfigServiceImpl(EncryptionService encryptionService) {
        this.dataSources = new HashMap<>();
        this.encryptionService = encryptionService;
    }

    @PostConstruct
    public void init() {
        if (docsDirectory == null || docsDirectory.isEmpty()) {
            docsDirectory = System.getProperty("user.home") + File.separator + "docs" + File.separator;
            log.warn("documents directory not set, using default: {}", docsDirectory);
        }
        log.info("Document root directory {}", docsDirectory);
        loadDataSourceConfigs();
        loadOtherConfigs();
    }

    private void loadDataSourceConfigs() {
        File dataSourceFile = new File(docsDirectory, "config/datasource.yaml");
        if (dataSourceFile.exists()) {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            try {
                Map<String, DataSourceConfig> configs = mapper.readValue(dataSourceFile,
                        mapper.getTypeFactory().constructMapType(Map.class, String.class, DataSourceConfig.class));
                var x = configs.entrySet().stream()
                        .map(e -> {
                            String rawPassword = e.getValue().password();
                            String password = decryptPassword(e.getKey(), rawPassword);
                            return new DataSourceConfig(e.getKey(), e.getValue().url(),
                                    e.getValue().username(), password);
                        })
                        .collect(Collectors.toMap(DataSourceConfig::name, v -> v));
                this.dataSources.putAll(x);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load datasource configurations", e);
            }
        }
    }

    /**
     * Decrypts a password from disk.  If the value is an {@code ENC(...)} token:
     * <ul>
     *   <li>Key is set → decrypt and return plain text.</li>
     *   <li>Key is not set → warn and return the raw token (DB connect will fail until
     *       a passphrase is provided at {@code /config/encryption-key}).</li>
     * </ul>
     * Plain-text passwords (no prefix) are returned unchanged.
     */
    private String decryptPassword(String datasourceName, String rawPassword) {
        if (encryptionService == null || !EncryptionService.isEncrypted(rawPassword)) {
            return rawPassword;
        }
        if (encryptionService.isKeySet()) {
            return encryptionService.decrypt(rawPassword);
        }
        log.warn("Datasource '{}' has an encrypted password but no passphrase has been provided. "
                + "Visit /config/encryption-key to activate encryption.", datasourceName);
        return rawPassword;
    }

    public void updateDataSources(Map<String, String> newConfigs) {
        Map<String, DataSourceConfig> updatedDataSources = new HashMap<>(dataSources);

        for (Map.Entry<String, String> entry : newConfigs.entrySet()) {
            String[] parts = entry.getKey().split("\\.");
            if (parts.length == 2 && parts[0].startsWith("datasources[") && parts[0].endsWith("]")) {
                String dataSourceName = parts[0].substring(12, parts[0].length() - 1);
                String property = parts[1];
                DataSourceConfig config = updatedDataSources.getOrDefault(dataSourceName, new DataSourceConfig(dataSourceName, "", "", ""));

                config = switch (property) {
                    case "url" -> new DataSourceConfig(dataSourceName, entry.getValue(), config.username(), config.password());
                    case "username" -> new DataSourceConfig(dataSourceName, config.url(), entry.getValue(), config.password());
                    case "password" -> !entry.getValue().equals("********") ? new DataSourceConfig(dataSourceName, config.url(), config.username(), entry.getValue()) : config;
                    default -> config;
                };

                updatedDataSources.put(dataSourceName, config);
            }
        }

        this.dataSources = updatedDataSources;
    }

    public void saveAndReloadConfig() {
        saveOtherConfigs();
        saveDataSourceConfigs();
        loadDataSourceConfigs();
        loadOtherConfigs();
    }

    @Override
    public void reEncryptAndSave() {
        this.dataSources.clear();
        loadDataSourceConfigs();   // decrypts ENC(...) tokens now that key is set
        saveDataSourceConfigs();   // re-writes with all passwords encrypted
        log.info("Datasource passwords re-encrypted and saved to disk");
    }

    @Override
    public boolean isEncryptionKeySet() {
        return encryptionService != null && encryptionService.isKeySet();
    }

    private void loadOtherConfigs() {
        File otherConfigsFile = new File(docsDirectory, "config/config.yaml");
        if (otherConfigsFile.exists()) {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            try {
                OtherConfigs otherConfigs = mapper.readValue(otherConfigsFile, OtherConfigs.class);
                this.sqlMaxRows = otherConfigs.sqlMaxRows();
                this.chromeDriverLocation = otherConfigs.chromeDriverLocation();
            } catch (IOException e) {
                throw new RuntimeException("Failed to load other configurations", e);
            }
            log.info("Loaded other configurations");
        }
    }

    private void saveOtherConfigs() {
        File otherConfigsFile = new File(docsDirectory, "config/config.yaml");
        otherConfigsFile.getParentFile().mkdirs();
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {
            mapper.writeValue(otherConfigsFile, new OtherConfigs(sqlMaxRows, chromeDriverLocation));
        } catch (IOException e) {
            throw new RuntimeException("Failed to save other configurations", e);
        }
    }

    private void saveDataSourceConfigs() {
        File dataSourceFile = new File(docsDirectory, "config/datasource.yaml");
        dataSourceFile.getParentFile().mkdirs();
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {
            mapper.writeValue(dataSourceFile, buildEncryptedConfigs());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save datasource configurations", e);
        }
    }

    /**
     * Returns a copy of {@code dataSources} where every plain-text password has been
     * replaced with an {@code ENC(...)} token (when a key is active).  The in-memory
     * {@code dataSources} map is not mutated — passwords remain plain text there.
     */
    private Map<String, DataSourceConfig> buildEncryptedConfigs() {
        if (encryptionService == null || !encryptionService.isKeySet()) {
            return dataSources;
        }
        return dataSources.entrySet().stream()
                .map(e -> {
                    String pwd = e.getValue().password();
                    String stored = EncryptionService.isEncrypted(pwd) ? pwd : encryptionService.encrypt(pwd);
                    return new DataSourceConfig(e.getKey(), e.getValue().url(),
                            e.getValue().username(), stored);
                })
                .collect(Collectors.toMap(DataSourceConfig::name, v -> v));
    }

    @Override
    public DataSourceConfig getDataSourceConfig(String name) {
        return dataSources.get(name);
    }

    @Override
    public Optional<String> getSshKey() {
        if (sshKeyFile == null || sshKeyFile.isEmpty() || sshKeyFile.isBlank()) {
            return Optional.empty();
        } else {
            return Optional.of(sshKeyFile);
        }
    }

    @Override
    public Optional<String> getChromeDriverLocation() {
        if (chromeDriverLocation == null || chromeDriverLocation.isEmpty() || chromeDriverLocation.isBlank()) {
            return Optional.empty();
        } else {
            return Optional.of(chromeDriverLocation);
        }
    }
}