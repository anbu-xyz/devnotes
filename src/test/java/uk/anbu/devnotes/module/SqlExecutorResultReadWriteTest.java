package uk.anbu.devnotes.module;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.resolve.ResourceCodeResolver;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import uk.anbu.devnotes.module.sql.SqlOutput;
import uk.anbu.devnotes.service.ConfigService;

import java.io.StringReader;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SqlExecutorResultReadWriteTest {
    private ConfigService configService;
    private TemplateEngine templateEngine;
    private SqlExecutor sqlExecutor;

    @TempDir
    Path tempDir;

    private DriverManagerDataSource dataSource;

    @BeforeEach
    void setUp() throws SQLException {
        // Set up ConfigService
        configService = new ConfigService();
        configService.setDocsDirectory(tempDir.toString());
        configService.setSqlMaxRows(1000);

        // Set up TemplateEngine
        templateEngine = TemplateEngine.create(new ResourceCodeResolver("jte"), ContentType.Html);

        // Set up SqlExecutor
        ObjectMapper objectMapper = new ObjectMapper();
        sqlExecutor = new SqlExecutor(objectMapper, templateEngine, configService);

        // Set up H2 in-memory database
        dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        // Create test table and insert data
        try (Connection conn = dataSource.getConnection()) {
            RunScript.execute(conn, new StringReader(
                    "CREATE TABLE test_table (id INT PRIMARY KEY, name VARCHAR(255), amount DOUBLE);" +
                            "INSERT INTO test_table VALUES (1, 'Test1', 10.5);" +
                            "INSERT INTO test_table VALUES (2, 'Test2', 20.7);" +
                            "INSERT INTO test_table VALUES (3, 'Test3', 30.9);"
            ));
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        // Drop the test table
        try (Connection conn = dataSource.getConnection()) {
            RunScript.execute(conn, new StringReader("DROP TABLE IF EXISTS test_table;"));
        }
    }

    @Test
    void testWriteAndReadBackJson() throws Exception {
        // Prepare test data
        var dataSourceConfig = new ConfigService.DataSourceConfig("testDB", dataSource.getUrl(), dataSource.getUsername(),
                dataSource.getPassword(), "org.h2.Driver");
        configService.getDataSources().put("testDB", dataSourceConfig);

        String sql = "SELECT * FROM test_table WHERE amount > :minValue";
        Map<String, String> parameterValues = new HashMap<>();
        parameterValues.put("minValue", "15.0");
        String markdownFilePath = "test.md";

        // Write JSON file
        SqlExecutor.JsonGenerationRequest request = new SqlExecutor.JsonGenerationRequest(dataSourceConfig, sql,
                parameterValues, markdownFilePath,  Integer.MAX_VALUE, false);
        Path jsonFilePath = sqlExecutor.renderResultAsJsonFile(request);

        SqlOutput output = SqlOutput.fromJson(jsonFilePath.toString());

        assertNotNull(output);
        assertEquals(sql, output.getSql().getSqlText());
        assertEquals(dataSourceConfig.name(), output.getDatasourceName());
        assertFalse(output.isDbHasMoreRowsThanMaxConfig());

        // Verify metadata
        assertNotNull(output.getMetadata());
        assertEquals(3, output.getMetadata().size());
        assertEquals("ID", output.getMetadata().get(0).getName());
        assertEquals("NAME", output.getMetadata().get(1).getName());
        assertEquals("AMOUNT", output.getMetadata().get(2).getName());

        // Verify data
        assertNotNull(output.getData());
        assertEquals(2, output.getData().size());

        Map<String, Object> firstRow = output.getData().get(0);
        assertEquals(2, firstRow.get("ID"));
        assertEquals("Test2", firstRow.get("NAME"));
        assertEquals(20.7, firstRow.get("AMOUNT"));

        Map<String, Object> secondRow = output.getData().get(1);
        assertEquals(30.9, secondRow.get("AMOUNT"));
        assertEquals("Test3", secondRow.get("NAME"));
        assertEquals(3, secondRow.get("ID"));
    }
}
