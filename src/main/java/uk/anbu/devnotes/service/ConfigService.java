package uk.anbu.devnotes.service;

import java.util.Optional;

public interface ConfigService {
    DataSourceConfig getDataSourceConfig(String name);

    Optional<String> getSshKey();

    Optional<String> getChromeDriverLocation();

    String getDocsDirectory();

    String getSshKeyFile();

    int getSqlMaxRows();

    java.util.Map<String, ConfigServiceImpl.DataSourceConfig> getDataSources();

    record DataSourceConfig(String name, String url, String username, String password, String driverClassName) {
    }

    record OtherConfigs(int sqlMaxRows, String chromeDriverLocation) {
    }
}
