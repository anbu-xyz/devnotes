package uk.anbu.devnotes.controller

import com.fasterxml.jackson.databind.ObjectMapper
import gg.jte.ContentType
import gg.jte.TemplateEngine
import gg.jte.resolve.DirectoryCodeResolver
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.http.HttpStatus
import spock.lang.Specification
import uk.anbu.devnotes.module.sql.SqlExecutor
import uk.anbu.devnotes.service.ConfigService
import uk.anbu.devnotes.types.MarkdownFile

import java.nio.file.Files
import java.nio.file.Paths

class SqlExecutionControllerSpec extends Specification {

    SqlExecutionController controller
    SqlExecutor sqlExecutor
    ConfigService configService
    TemplateEngine templateEngine
    ObjectMapper objectMapper

    def setup() {
        sqlExecutor = Mock(SqlExecutor)
        configService = Mock(ConfigService)
        objectMapper = new ObjectMapper()
        def codeResolver = new DirectoryCodeResolver(Paths.get("src/main/jte"))
        templateEngine = TemplateEngine.create(codeResolver, Paths.get("src/main/jte"), ContentType.Html)
        controller = new SqlExecutionController(configService, sqlExecutor, objectMapper, templateEngine)
    }

    def "reExecuteSql GET should render sql-executor template with available datasources"() {
        given:
        def dataSources = ["db1": null, "db2": null]
        configService.getDataSources() >> dataSources

        when:
        def response = controller.reExecuteSql()

        then:
        response.statusCode == HttpStatus.OK
        Document doc = Jsoup.parse(response.body)
        def options = doc.select("#datasourceName option")
        options.size() == 2
        options*.text() == ["db1", "db2"]
    }

    def "reExecuteSql POST should execute SQL and return HTML table"() {
        given:
        def request = new SqlExecutionController.SqlExecutionRequest(
                datasourceName: "testDB",
                sql: "SELECT * FROM test",
                markdownFileName: "test.md",
                codeBlockCounter: 1,
                parameterValues: [:],
                maxRows: 10,
                forceExecute: true
        )
        def dataSourceConfig = new ConfigService.DataSourceConfig("testDB", "jdbc:test:url", "user", "pass", "driver")
        def tempDir = Files.createTempDirectory("test")
        def jsonFile = tempDir.resolve("output.json")

        configService.getDataSourceConfig("testDB") >> dataSourceConfig
        configService.getDocsDirectory() >> tempDir.toString()
        sqlExecutor.renderResultAsJsonFile(_ as SqlExecutor.JsonGenerationRequest) >> jsonFile
        sqlExecutor.convertToHtmlTable(_ as SqlExecutor.HtmlTableRequest) >> "<table>Test Result</table>"

        when:
        def response = controller.reExecuteSql(request)

        then:
        response.statusCode == HttpStatus.OK
        response.body == "<table>Test Result</table>"

        cleanup:
        tempDir.deleteDir()
    }

    def "reExecuteSql POST should handle invalid datasource"() {
        given:
        def request = new SqlExecutionController.SqlExecutionRequest(
                datasourceName: "invalidDB",
                sql: "SELECT * FROM test",
                markdownFileName: "test.md",
                codeBlockCounter: 1,
                parameterValues: [:],
                maxRows: 10,
                forceExecute: true
        )
        configService.getDataSourceConfig("invalidDB") >> null

        when:
        def response = controller.reExecuteSql(request)

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
        response.body == "Invalid datasource name:invalidDB"
    }

    def "sortTable should sort data and return updated HTML"() {
        given:
        def request = new SqlExecutionController.TableSortRequest(
                datasourceName: "testDB",
                columnName: "name",
                columnType: "java.lang.String",
                outputFileName: "output.json",
                markdownFileName: "test.md",
                sortDirection: "asc",
                codeBlockCounter: 1
        )
        def tempDir = Files.createTempDirectory("test")
        def markdownFile = tempDir.resolve("test.md")
        Files.write(markdownFile, """# Test""".bytes)
        def jsonFile = tempDir.resolve("output.json")
        Files.createDirectories(jsonFile.parent)
        objectMapper.writeValue(jsonFile.toFile(), [
                data: [
                        [name: "Bob"],
                        [name: "Alice"]
                ]
        ])

        configService.getDocsDirectory() >> tempDir.toString()
        sqlExecutor.convertToHtmlTable(_, "testDB", _ as MarkdownFile, "output.json", "name", "asc", 1) >> "<table>Sorted Result</table>"

        when:
        def response = controller.sortTable(request)

        then:
        response.statusCode == HttpStatus.OK
        response.body == "<table>Sorted Result</table>"

        cleanup:
        tempDir.deleteDir()
    }

    def "saveSqlChanges should update markdown file and re-execute SQL"() {
        given:
        def request = new SqlExecutionController.SqlChangeRequest(
                datasourceName: "testDB",
                markdownFileName: "test.md",
                oldSql: "SELECT * FROM old",
                newSql: "SELECT * FROM new",
                maxRows: 10,
                codeBlockCounter: 0,
                parameterValues: [:],
                forceExecute: true
        )
        def tempDir = Files.createTempDirectory("test")
        def markdownFile = tempDir.resolve("test.md")
        Files.write(markdownFile, """# Test
```sql
SELECT * FROM old
```
""".bytes)
        configService.getDocsDirectory() >> tempDir.toString()
        def dataSourceConfig = new ConfigService.DataSourceConfig("testDB", "jdbc:test:url", "user", "pass", "driver")
        configService.getDataSourceConfig("testDB") >> dataSourceConfig
        sqlExecutor.renderResultAsJsonFile(_ as SqlExecutor.JsonGenerationRequest) >> tempDir.resolve("output.json")
        sqlExecutor.convertToHtmlTable(_ as SqlExecutor.HtmlTableRequest) >> "<table>Updated Result</table>"

        when:
        def response = controller.saveSqlChanges(request)

        then:
        response.statusCode == HttpStatus.OK
        response.body == "<table>Updated Result</table>"
        Files.readString(markdownFile).contains("SELECT * FROM new")
        !Files.readString(markdownFile).contains("SELECT * FROM old")

        cleanup:
        tempDir.deleteDir()
    }

    def "downloadExcel should return Excel file"() {
        given:
        def tempDir = Files.createTempDirectory("test")
        def excelFile = tempDir.resolve("test.xlsx")
        Files.write(excelFile, "test".bytes)

        sqlExecutor.createExcelFile("output.json", "test.md") >> excelFile.toFile()

        when:
        def response = controller.downloadExcel("output.json", "test.md")

        then:
        response.statusCode == HttpStatus.OK
        response.headers.getContentType().toString() == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        response.headers.getContentDisposition().toString() == 'attachment; filename="sql_results.xlsx"'

        cleanup:
        tempDir.deleteDir()
    }
}