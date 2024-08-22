package uk.anbu.devnotes.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigServiceTest {

    private ConfigService configService;

    @BeforeEach
    void setUp() {
        configService = new ConfigService();
        // Initialize with a sample data source
        Map<String, ConfigService.DataSourceConfig> initialDataSources = new HashMap<>();
        initialDataSources.put("testDB", new ConfigService.DataSourceConfig("testDB", "jdbc:test:url", "testUser", "testPass", "org.test.Driver"));
        configService.setDataSources(initialDataSources);
    }

    @Test
    void testUpdateExistingDataSource() {
        Map<String, String> newConfigs = new HashMap<>();
        newConfigs.put("datasources[testDB].url", "jdbc:new:url");
        newConfigs.put("datasources[testDB].username", "newUser");

        configService.updateDataSources(newConfigs);

        ConfigService.DataSourceConfig updatedConfig = configService.getDataSourceConfig("testDB");
        assertEquals("jdbc:new:url", updatedConfig.url());
        assertEquals("newUser", updatedConfig.username());
        assertEquals("testPass", updatedConfig.password()); // Should remain unchanged
        assertEquals("org.test.Driver", updatedConfig.driverClassName()); // Should remain unchanged
    }

    @Test
    void testAddNewDataSource() {
        Map<String, String> newConfigs = new HashMap<>();
        newConfigs.put("datasources[newDB].url", "jdbc:new:url");
        newConfigs.put("datasources[newDB].username", "newUser");
        newConfigs.put("datasources[newDB].password", "newPass");
        newConfigs.put("datasources[newDB].driverClassName", "org.new.Driver");

        configService.updateDataSources(newConfigs);

        ConfigService.DataSourceConfig newConfig = configService.getDataSourceConfig("newDB");
        assertNotNull(newConfig);
        assertEquals("jdbc:new:url", newConfig.url());
        assertEquals("newUser", newConfig.username());
        assertEquals("newPass", newConfig.password());
        assertEquals("org.new.Driver", newConfig.driverClassName());
    }

    @Test
    void testUpdatePassword() {
        Map<String, String> newConfigs = new HashMap<>();
        newConfigs.put("datasources[testDB].password", "newPassword");

        configService.updateDataSources(newConfigs);

        ConfigService.DataSourceConfig updatedConfig = configService.getDataSourceConfig("testDB");
        assertEquals("newPassword", updatedConfig.password());
    }

    @Test
    void testPasswordNotUpdatedWhenAsterisk() {
        Map<String, String> newConfigs = new HashMap<>();
        newConfigs.put("datasources[testDB].password", "********");

        configService.updateDataSources(newConfigs);

        ConfigService.DataSourceConfig updatedConfig = configService.getDataSourceConfig("testDB");
        assertEquals("testPass", updatedConfig.password()); // Should remain unchanged
    }

    @Test
    void testIgnoreUnrelatedConfigs() {
        Map<String, String> newConfigs = new HashMap<>();
        newConfigs.put("unrelated.config", "someValue");
        newConfigs.put("datasources[testDB].url", "jdbc:new:url");

        configService.updateDataSources(newConfigs);

        ConfigService.DataSourceConfig updatedConfig = configService.getDataSourceConfig("testDB");
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

        ConfigService.DataSourceConfig updatedTestDB = configService.getDataSourceConfig("testDB");
        ConfigService.DataSourceConfig updatedAnotherDB = configService.getDataSourceConfig("anotherDB");

        assertEquals("jdbc:new:url", updatedTestDB.url());
        assertEquals("anotherUser", updatedAnotherDB.username());
    }
}