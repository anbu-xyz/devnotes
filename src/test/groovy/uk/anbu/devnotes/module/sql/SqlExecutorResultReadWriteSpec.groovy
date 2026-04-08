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
import uk.anbu.devnotes.service.ConfigServiceImpl
import uk.anbu.devnotes.types.MarkdownFile

import java.nio.file.Path

class SqlExecutorResultReadWriteSpec extends Specification {
    @TempDir
    Path tempDir

    ConfigService configService
    SqlExecutor sqlExecutor
    DriverManagerDataSource dataSource
    ConfigService.DataSourceConfig dataSourceConfig

    def setup() {
        // Set up ConfigService
        configService = new ConfigServiceImpl()
        configService.docsDirectory = tempDir.toString()
        configService.sqlMaxRows = 1000

        // Set up TemplateEngine
        def templateEngine = TemplateEngine.create(new ResourceCodeResolver("jte"), ContentType.Html)

        // Set up SqlExecutor
        def objectMapper = new ObjectMapper()
        sqlExecutor = new SqlExecutor(objectMapper, templateEngine, configService)

        // Set up H2 in-memory database
        dataSource = new DriverManagerDataSource()
        dataSource.url = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1"
        dataSource.username = "sa"
        dataSource.password = ""

        dataSourceConfig = new ConfigService.DataSourceConfig("testDB",
                dataSource.url,
                dataSource.username,
                dataSource.password)
        configService.dataSources.put("testDB", dataSourceConfig)

        setupTestData()
    }

    private void setupTestData() {
        dataSource.connection.withCloseable { conn ->
            RunScript.execute(conn, new StringReader("""
                CREATE TABLE test_table (
                    id INT PRIMARY KEY, 
                    name VARCHAR(255), 
                    amount DOUBLE,
                    created_at TIMESTAMP,
                    updated_at TIMESTAMP WITH TIME ZONE,
                    description CLOB,
                    binary_data BLOB
                );
                
                INSERT INTO test_table VALUES (1, 'Test1', 10.5, '2023-01-01 10:00:00', '2023-01-01 10:00:00+00', 'Long text here', null);
                INSERT INTO test_table VALUES (2, 'Test2', 20.7, '2023-01-02 10:00:00', '2023-01-02 10:00:00+00', 'Another long text', null);
                INSERT INTO test_table VALUES (3, 'Test3', 30.9, '2023-01-03 10:00:00', '2023-01-03 10:00:00+00', 'Yet another text', null);
            """))
        }
    }

    def cleanup() {
        dataSource.connection.withCloseable { conn -> RunScript.execute(conn, new StringReader("DROP TABLE IF EXISTS test_table;"))
        }
    }

    def "should write and read back JSON with various data types"() {
        given: "a SQL query with different data types"
        def sql = "SELECT * FROM test_table WHERE amount > :minValue"
        def parameterValues = [minValue: "15.0"]
        def markdownFile = new MarkdownFile(Path.of(configService.docsDirectory), "test.md")

        when: "generating and reading JSON file"
        def request = new SqlExecutor.JsonGenerationRequest(dataSourceConfig,
                sql,
                parameterValues,
                markdownFile,
                Integer.MAX_VALUE,
                false)
        def jsonFilePath = sqlExecutor.renderResultAsJsonFile(request)
        def output = SqlOutput.fromJson(jsonFilePath.toString())

        then: "output contains correct metadata"
        output.metadata.size() == 7
        output.metadata*.name == ['ID', 'NAME', 'AMOUNT', 'CREATED_AT', 'UPDATED_AT', 'DESCRIPTION', 'BINARY_DATA']
        output.metadata*.type == ['java.lang.Integer',
                                  'java.lang.String',
                                  'java.lang.Double',
                                  'java.sql.Timestamp',
                                  'java.time.OffsetDateTime',
                                  'java.sql.Clob',
                                  'java.sql.Blob']

        and: "data is correctly handled"
        output.data.size() == 2
        with(output.data[0]) {
            it.ID == 2
            it.NAME == "Test2"
            it.AMOUNT == 20.7
            it.DESCRIPTION == "<clob>"
            it.BINARY_DATA == null
        }
    }

    def "should handle max rows configuration"() {
        given: "a SQL query with max rows set to 1"
        def sql = "SELECT * FROM test_table ORDER BY id"
        def markdownFile = new MarkdownFile(Path.of(configService.docsDirectory), "test.md")

        when: "executing query with max rows = 1"
        def request = new SqlExecutor.JsonGenerationRequest(dataSourceConfig,
                sql,
                [:],
                markdownFile,
                1,
                false)
        def jsonFilePath = sqlExecutor.renderResultAsJsonFile(request)
        def output = SqlOutput.fromJson(jsonFilePath.toString())

        then: "only one row is returned and more rows indicator is set"
        output.data.size() == 1
        output.dbHasMoreRowsThanMaxConfig
    }

    def "should handle HTML table conversion"() {
        given: "a SQL result file"
        def sql = "SELECT * FROM test_table WHERE amount > :minValue"
        def markdownFile = new MarkdownFile(Path.of(configService.docsDirectory), "test.md")
        def request = new SqlExecutor.JsonGenerationRequest(dataSourceConfig,
                sql,
                [minValue: "15.0"],
                markdownFile,
                Integer.MAX_VALUE,
                false)
        def jsonFilePath = sqlExecutor.renderResultAsJsonFile(request)

        when: "converting to HTML table"
        def htmlTableRequest = new SqlExecutor.HtmlTableRequest(sql,
                jsonFilePath,
                [minValue: "15.0"],
                "testDB",
                markdownFile,
                1)
        def result = sqlExecutor.getResult(htmlTableRequest)

        then: "result contains correct data"
        !result.isError
        result.data.rowData.size() == 2
        result.datasourceName == "testDB"
        result.sql.sqlText == sql
    }

    def "should correctly sort data"() {
        given: "some test data"
        def data = [[ID: 1, NAME: "Test1", AMOUNT: 10.5],
                    [ID: 2, NAME: "Test2", AMOUNT: 20.7],
                    [ID: 3, NAME: "Test3", AMOUNT: 30.9]]

        when: "sorting by different columns and directions"
        def sortedAscById = SqlExecutor.sortData(data, "ID", "java.lang.Integer", "asc")

        then: "data is correctly sorted"
        sortedAscById*.ID == [1, 2, 3]

        when:
        def sortedDescById = SqlExecutor.sortData(data, "ID", "java.lang.Integer", "desc")

        then:
        sortedDescById*.ID == [3, 2, 1]

        when:
        def sortedAscByAmount = SqlExecutor.sortData(data, "AMOUNT", "java.lang.Double", "asc")

        then:
        sortedAscByAmount*.AMOUNT == [10.5, 20.7, 30.9]
    }

    def "should handle human readable numbers for various number formats"() {
        expect: "numbers are formatted correctly"
        SqlExecutor.humanReadableNumber(input).toString() == expected

        where:
        input                   | expected
        null                   | "(null)"
        0                      | "0"
        1000                   | "1,000"
        1000000                | "1,000,000"
        1234567890             | "1,234,567,890"
        1234567890.12345       | "1,234,567,890.12345"
        0.1234567890           | "0.123456789"
        new BigDecimal("1E20") | "100,000,000,000,000,000,000"
        new BigDecimal("1E-10")| "0.0000000001"
        Double.MAX_VALUE       | "179,769,313,486,231,570,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000"
        Double.MIN_VALUE       | "0.0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000049"
        new BigDecimal("0.00001") | "0.00001"
        new BigDecimal("-1234567890.12345") | "-1,234,567,890.12345"
        new BigDecimal("9999999999999999999999") | "9,999,999,999,999,999,999,999"
    }

    def "should sort data with integers correctly"() {
        given: "a list of data with integer values"
        def data = [
                [id: 3, name: "Charlie"],
                [id: 1, name: "Alice"],
                [id: 2, name: "Bob"]
        ]

        when: "sorting by id ascending"
        def result = SqlExecutor.sortData(data, "id", "java.lang.Integer", "asc")

        then: "data is sorted correctly"
        result*.id == [1, 2, 3]
        result*.name == ["Alice", "Bob", "Charlie"]
    }

    def "should sort data with strings correctly"() {
        given: "a list of data with string values"
        def data = [
                [id: 1, name: "Charlie"],
                [id: 2, name: "Alice"],
                [id: 3, name: "Bob"]
        ]

        when: "sorting by name descending"
        def result = SqlExecutor.sortData(data, "name", "java.lang.String", "desc")

        then: "data is sorted correctly"
        result*.name == ["Charlie", "Bob", "Alice"]
    }

    def "should sort data with doubles correctly"() {
        given: "a list of data with double values"
        def data = [
                [id: 1, score: 3.5],
                [id: 2, score: 1.2],
                [id: 3, score: 2.8]
        ]

        when: "sorting by score ascending"
        def result = SqlExecutor.sortData(data, "score", "java.lang.Double", "asc")

        then: "data is sorted correctly"
        result*.score == [1.2, 2.8, 3.5]
    }

    def "should sort data with null values correctly"() {
        given: "a list of data with null values"
        def data = [
                [id: 1, name: "Alice"],
                [id: 2, name: null],
                [id: 3, name: "Bob"]
        ]

        when: "sorting by name ascending"
        def result = SqlExecutor.sortData(data, "name", "java.lang.String", "asc")

        then: "nulls come first, followed by sorted values"
        result*.name == [null, "Alice", "Bob"]
    }

    def "should sort data with timestamps correctly"() {
        given: "a list of data with timestamp values"
        def data = [
                [id: 1, timestamp: "2023-05-01 10:00:00"],
                [id: 2, timestamp: "2023-05-01 09:00:00"],
                [id: 3, timestamp: "2023-05-01 11:00:00"]
        ]

        when: "sorting by timestamp ascending"
        def result = SqlExecutor.sortData(data, "timestamp", "java.sql.Timestamp", "asc")

        then: "timestamps are sorted correctly"
        result*.timestamp == [
                "2023-05-01 09:00:00",
                "2023-05-01 10:00:00",
                "2023-05-01 11:00:00"
        ]
    }

    def "should handle error conditions in SQL execution"() {
        given: "an invalid SQL query"
        def sql = "SELECT * FROM non_existent_table"
        def markdownFile = new MarkdownFile(Path.of(configService.docsDirectory), "test.md")

        when: "executing invalid query"
        def request = new SqlExecutor.JsonGenerationRequest(
                dataSourceConfig,
                sql,
                [:],
                markdownFile,
                Integer.MAX_VALUE,
                false
        )
        def jsonFilePath = sqlExecutor.renderResultAsJsonFile(request)
        def output = SqlOutput.fromJson(jsonFilePath.toString())

        then: "error metadata is present"
        output.metadata.size() == 1
        output.metadata[0].name == "Error"
        output.metadata[0].type == "java.lang.String"
        output.data.size() == 1
        output.data[0].Error.contains("non_existent_table")
    }
}