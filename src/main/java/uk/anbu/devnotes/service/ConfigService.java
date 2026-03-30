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

    /** Returns {@code true} when an encryption passphrase has been activated in memory. */
    boolean isEncryptionKeySet();

    /**
     * Re-reads datasource passwords from disk (decrypting any {@code ENC(...)} tokens with
     * the current key), then writes them back encrypted.  Call this immediately after a new
     * passphrase has been accepted so that any previously plain-text passwords are encrypted.
     */
    void reEncryptAndSave();

    record DataSourceConfig(String name, String url, String username, String password, String driverClassName) {
    }

    record OtherConfigs(int sqlMaxRows, String chromeDriverLocation) {
    }
}
