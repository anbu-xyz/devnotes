package uk.anbu.devnotes.module.sql

import com.fasterxml.jackson.databind.ObjectMapper
import gg.jte.ContentType
import gg.jte.TemplateEngine
import gg.jte.resolve.ResourceCodeResolver
import org.h2.tools.RunScript
import org.springframework.jdbc.datasource.DriverManagerDataSource
import spock.lang.Specification
import spock.lang.TempDir
import uk.anbu.devnotes.service.ConfigService
import uk.anbu.devnotes.types.MarkdownFile

import java.nio.file.Path

class SqlExecutorResultReadWriteSpec extends Specification {
    @TempDir
    Path tempDir

    ConfigService configService
    SqlExecutor sqlExecutor
    DriverManagerDataSource dataSource

    def setup() {
        // Set up ConfigService
        configService = new ConfigService()
        configService.docsDirectory = tempDir.toString()
        configService.sqlMaxRows = 1000

        // Set up TemplateEngine
        def templateEngine = TemplateEngine.create(new ResourceCodeResolver("jte"), ContentType.Html)

        // Set up SqlExecutor
        def objectMapper = new ObjectMapper()
        sqlExecutor = new SqlExecutor(objectMapper, templateEngine, configService)

        // Set up H2 in-memory database
        dataSource = new DriverManagerDataSource()
        dataSource.driverClassName = "org.h2.Driver"
        dataSource.url = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1"
        dataSource.username = "sa"
        dataSource.password = ""

        // Create test table and insert data
        dataSource.connection.withCloseable { conn ->
            RunScript.execute(conn, new StringReader("""
                CREATE TABLE test_table (id INT PRIMARY KEY, name VARCHAR(255), amount DOUBLE);
                INSERT INTO test_table VALUES (1, 'Test1', 10.5);
                INSERT INTO test_table VALUES (2, 'Test2', 20.7);
                INSERT INTO test_table VALUES (3, 'Test3', 30.9);
            """))
        }
    }

    def cleanup() {
        dataSource.connection.withCloseable { conn ->
            RunScript.execute(conn, new StringReader("DROP TABLE IF EXISTS test_table;"))
        }
    }

    def "should write and read back JSON correctly"() {
        given: "a configured data source and SQL query"
        def dataSourceConfig = new ConfigService.DataSourceConfig(
                "testDB",
                dataSource.url,
                dataSource.username,
                dataSource.password,
                "org.h2.Driver"
        )
        configService.dataSources.put("testDB", dataSourceConfig)

        def sql = "SELECT * FROM test_table WHERE amount > :minValue"
        def parameterValues = [minValue: "15.0"]
        def markdownFilePath = "test.md"
        def markdownFile = new MarkdownFile(Path.of(configService.docsDirectory), markdownFilePath)

        when: "generating and reading JSON file"
        def request = new SqlExecutor.JsonGenerationRequest(
                dataSourceConfig,
                sql,
                parameterValues,
                markdownFile,
                Integer.MAX_VALUE,
                false
        )
        def jsonFilePath = sqlExecutor.renderResultAsJsonFile(request)
        def output = SqlOutput.fromJson(jsonFilePath.toString())

        then: "output contains correct SQL and datasource information"
        output != null
        output.sql.sqlText == sql
        output.datasourceName == dataSourceConfig.name()
        !output.dbHasMoreRowsThanMaxConfig

        and: "metadata is correct"
        output.metadata.size() == 3
        output.metadata[0].name == "ID"
        output.metadata[1].name == "NAME"
        output.metadata[2].name == "AMOUNT"

        and: "data rows are correct"
        output.data.size() == 2

        with(output.data[0]) {
            it.ID == 2
            it.NAME == "Test2"
            it.AMOUNT == 20.7
        }

        with(output.data[1]) {
            it.ID == 3
            it.NAME == "Test3"
            it.AMOUNT == 30.9
        }
    }
}