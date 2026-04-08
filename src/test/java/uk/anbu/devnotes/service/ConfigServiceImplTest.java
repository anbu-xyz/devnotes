package uk.anbu.devnotes.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigServiceImplTest {

    private ConfigServiceImpl configService;

    @BeforeEach
    void setUp() {
        configService = new ConfigServiceImpl(new EncryptionService());
        // Initialize with a sample data source
        Map<String, ConfigServiceImpl.DataSourceConfig> initialDataSources = new HashMap<>();
        initialDataSources.put("testDB", new ConfigServiceImpl.DataSourceConfig("testDB", "jdbc:test:url",
                "testUser", "testPass"));
        configService.setDataSources(initialDataSources);
    }

    @Test
    void testUpdateExistingDataSource() {
        Map<String, String> newConfigs = new HashMap<>();
        newConfigs.put("datasources[testDB].url", "jdbc:new:url");
        newConfigs.put("datasources[testDB].username", "newUser");

        configService.updateDataSources(newConfigs);

        ConfigServiceImpl.DataSourceConfig updatedConfig = configService.getDataSourceConfig("testDB");
        assertEquals("jdbc:new:url", updatedConfig.url());
        assertEquals("newUser", updatedConfig.username());
        assertEquals("testPass", updatedConfig.password()); // Should remain unchanged
    }

    @Test
    void testAddNewDataSource() {
        Map<String, String> newConfigs = new HashMap<>();
        newConfigs.put("datasources[newDB].url", "jdbc:new:url");
        newConfigs.put("datasources[newDB].username", "newUser");
        newConfigs.put("datasources[newDB].password", "newPass");

        configService.updateDataSources(newConfigs);

        ConfigService.DataSourceConfig newConfig = configService.getDataSourceConfig("newDB");
        assertNotNull(newConfig);
        assertEquals("jdbc:new:url", newConfig.url());
        assertEquals("newUser", newConfig.username());
        assertEquals("newPass", newConfig.password());
    }

    @Test
    void testUpdatePassword() {
        Map<String, String> newConfigs = new HashMap<>();
        newConfigs.put("datasources[testDB].password", "newPassword");

        configService.updateDataSources(newConfigs);

        var updatedConfig = configService.getDataSourceConfig("testDB");
        assertEquals("newPassword", updatedConfig.password());
    }

    @Test
    void testPasswordNotUpdatedWhenAsterisk() {
        Map<String, String> newConfigs = new HashMap<>();
        newConfigs.put("datasources[testDB].password", "********");

        configService.updateDataSources(newConfigs);

        var updatedConfig = configService.getDataSourceConfig("testDB");
        assertEquals("testPass", updatedConfig.password()); // Should remain unchanged
    }

    @Test
    void testIgnoreUnrelatedConfigs() {
        Map<String, String> newConfigs = new HashMap<>();
        newConfigs.put("unrelated.config", "someValue");
        newConfigs.put("datasources[testDB].url", "jdbc:new:url");

        configService.updateDataSources(newConfigs);

        var updatedConfig = configService.getDataSourceConfig("testDB");
        assertEquals("jdbc:new:url", updatedConfig.url());
        assertEquals(1, configService.getDataSources().size()); // Should not add unrelated config
    }

    @Test
    void testUpdateMultipleDataSources() {
        // Add another data source first
        Map<String, String> addConfig = new HashMap<>();
        addConfig.put("datasources[anotherDB].url", "jdbc:another:url");
        configService.updateDataSources(addConfig);

        Map<String, String> newConfigs = new HashMap<>();
        newConfigs.put("datasources[testDB].url", "jdbc:new:url");
        newConfigs.put("datasources[anotherDB].username", "anotherUser");

        configService.updateDataSources(newConfigs);

        var updatedTestDB = configService.getDataSourceConfig("testDB");
        var updatedAnotherDB = configService.getDataSourceConfig("anotherDB");

        assertEquals("jdbc:new:url", updatedTestDB.url());
        assertEquals("anotherUser", updatedAnotherDB.username());
    }

    // -------------------------------------------------------------------------
    // Encryption tests
    // -------------------------------------------------------------------------

    @Test
    void testPasswordEncryptedOnSave(@TempDir Path tempDir) throws IOException {
        // Arrange
        Files.createDirectories(tempDir.resolve("config"));
        configService.setDocsDirectory(tempDir.toString());
        configService.setSqlMaxRows(1000);
        configService.setChromeDriverLocation("");

        EncryptionService encryptionService = new EncryptionService();
        Path saltFile = tempDir.resolve("config/encryption.salt");
        encryptionService.setPassphrase("my-test-passphrase-xyz", saltFile);
        configService.setEncryptionService(encryptionService);

        // Act
        configService.saveAndReloadConfig();

        // Assert — raw YAML must contain an ENC(...) token, not the plain password
        String yaml = Files.readString(tempDir.resolve("config/datasource.yaml"));
        assertTrue(yaml.contains("ENC("), "Password should be stored as ENC(...) token in YAML");
        assertFalse(yaml.contains("testPass"), "Plain-text password must not appear in YAML");

        // In-memory password should remain plain text after reload
        assertEquals("testPass", configService.getDataSourceConfig("testDB").password());
    }

    @Test
    void testPasswordDecryptedOnLoad(@TempDir Path tempDir) throws IOException {
        // Arrange: write a YAML file that already contains an ENC(...) token
        Files.createDirectories(tempDir.resolve("config"));
        configService.setDocsDirectory(tempDir.toString());
        configService.setSqlMaxRows(1000);
        configService.setChromeDriverLocation("");

        // Build an encrypted token for "secretPassword"
        EncryptionService encryptionService = new EncryptionService();
        Path saltFile = tempDir.resolve("config/encryption.salt");
        encryptionService.setPassphrase("my-test-passphrase-xyz", saltFile);

        String encryptedToken = encryptionService.encrypt("secretPassword");
        String yaml = "testDB:\n"
                + "  name: testDB\n"
                + "  url: jdbc:test:url\n"
                + "  username: testUser\n"
                + "  password: \"" + encryptedToken + "\"\n";
        Files.writeString(tempDir.resolve("config/datasource.yaml"), yaml);

        // Give the service an EncryptionService with the same passphrase + salt
        configService.setEncryptionService(encryptionService);

        // Act — reEncryptAndSave() clears in-memory, reloads from disk (decrypting), then saves back
        configService.reEncryptAndSave();

        // Assert — in-memory password must be the decrypted plain text
        assertEquals("secretPassword", configService.getDataSourceConfig("testDB").password());
    }

    @Test
    void testPlainPasswordToleratedWhenKeyIsSet(@TempDir Path tempDir) throws IOException {
        // Arrange: service has a plain-text password in memory (simulates pre-encryption state)
        Files.createDirectories(tempDir.resolve("config"));
        configService.setDocsDirectory(tempDir.toString());
        configService.setSqlMaxRows(1000);
        configService.setChromeDriverLocation("");

        EncryptionService encryptionService = new EncryptionService();
        Path saltFile = tempDir.resolve("config/encryption.salt");
        encryptionService.setPassphrase("my-test-passphrase-xyz", saltFile);
        configService.setEncryptionService(encryptionService);

        // Pre-populate in-memory with a plain-text password (no ENC token)
        Map<String, ConfigServiceImpl.DataSourceConfig> ds = new HashMap<>();
        ds.put("legacyDB", new ConfigServiceImpl.DataSourceConfig(
                "legacyDB", "jdbc:legacy:url", "legacyUser", "legacyPass"));
        configService.setDataSources(ds);

        // Act — saveAndReloadConfig: saves (encrypting legacyPass), then reloads (decrypting)
        assertDoesNotThrow(() -> configService.saveAndReloadConfig());

        // After reload the in-memory password must be plain text
        assertEquals("legacyPass", configService.getDataSourceConfig("legacyDB").password());

        // The on-disk file must contain the encrypted token
        String savedYaml = Files.readString(tempDir.resolve("config/datasource.yaml"));
        assertTrue(savedYaml.contains("ENC("), "Legacy plain password should be encrypted on next save");
        assertFalse(savedYaml.contains("legacyPass"), "Plain-text password must not appear in YAML");
    }
}