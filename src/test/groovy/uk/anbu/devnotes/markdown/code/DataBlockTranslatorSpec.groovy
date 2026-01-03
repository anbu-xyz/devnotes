package uk.anbu.devnotes.markdown.code

import org.commonmark.node.HtmlBlock
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Shared
import uk.anbu.devnotes.service.ConfigService
import uk.anbu.devnotes.types.MarkdownFile
import org.jsoup.Jsoup

import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

class DataBlockTranslatorSpec extends Specification {

    @TempDir
    Path tempDir

    @Shared
    String url = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1"
    @Shared
    String username = "sa"
    @Shared
    String password = ""
    @Shared
    String driver = "org.h2.Driver"
    @Shared
    Connection conn
    @Shared
    def resolver
    @Shared
    ConfigService configService
    @Shared
    DataBlockTranslator translator

    def setupSpec() {
        // create shared in-memory H2 and populate with two users
        conn = DriverManager.getConnection(url, username, password)
        conn.createStatement().execute("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100), email VARCHAR(100), password VARCHAR(100))")
        conn.createStatement().execute("INSERT INTO users (id, name, email, password) VALUES (1, 'Alice', 'alice@example.com', 'secret')")
        conn.createStatement().execute("INSERT INTO users (id, name, email, password) VALUES (2, 'Bob', 'bob@example.com', 'hunter2')")
        conn.createStatement().execute("INSERT INTO users (id, name, email, password) VALUES (3, 'Charlie', 'charlie@example.com', 'goodDay3')")

        resolver = { String name -> new ConfigService.DataSourceConfig(name, url, username, password, driver) }
        configService = Mock(ConfigService)
        configService.getSqlMaxRows() >> 100
        translator = new DataBlockTranslator(resolver, configService)
    }

    def cleanupSpec() {
        conn?.close()
    }

    def "source and query only returns a table with all columns"() {
        given:
        String yaml = '''
source: datasource1
query: SELECT id, name, email, password FROM users ORDER BY id
'''

        when:
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "example.md"))

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        def headers = doc.select("thead th").collect { it.text() }
        headers.containsAll(["id", "name", "email", "password"]*.toUpperCase())
    }

    def "header get rendered if present"() {
        given:
        String yaml = '''
header: User Information
source: datasource1
query: SELECT * FROM users
'''

        when:
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "example.md"))

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        def headers = doc.select("thead th").collect { it.text() }
        headers.contains("User Information")
    }

    def "limit option restricts the number of rows returned"() {
        given:
        String yaml = '''
source: datasource1
query: SELECT id, name, email, password FROM users ORDER BY id
options:
  row-limit: 1
'''

        when:
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "example.md"))

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        def rows = doc.select("tbody tr.data-block-data-row")
        rows.size() == 1
        def statusRow = doc.select("tbody tr.data-block-status-row")
        statusRow.size() == 1
        statusRow.first().text().contains("... max limit reached (1 rows)")
    }

    def "row count can be seen when rowcount exceeds threshold"() {
        given:
        String yaml = '''
source: datasource1
query: SELECT * FROM users
hide-row-count-when-less-than: 1
'''
        when:
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "example.md"))

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        def rows = doc.select("tbody tr.data-block-data-row")
        rows.size() == 3
        def statusRow = doc.select("tbody tr.data-block-status-row")
        statusRow.size() == 1
        statusRow.first().text().contains("Total rows: 3")
    }

    def "row count can not be seen when rowcount is under threshold"() {
        given:
        String yaml = '''
source: datasource1
query: SELECT * FROM users
hide-row-count-when-less-than: 10
'''
        when:
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "example.md"))

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        def rows = doc.select("tbody tr.data-block-data-row")
        rows.size() == 3
        def statusRow = doc.select("tbody tr.data-block-status-row")
        statusRow.size() == 0
    }

    def "specifying a smaller columns list returns only those columns"() {
        given:
        String yaml = '''
source: datasource1
query: SELECT * FROM users ORDER BY id
options:
  columns: ["name", "email"]
'''

        when:
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "example.md"))

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        def headers = doc.select("thead th").collect { it.text().toUpperCase() }
        headers == ["name", "email"]*.toUpperCase()

        def rows = doc.select("tbody tr.data-block-data-row")
        rows.size() == 3
        def firstCells = rows[0].select("td")
        firstCells[0].text() == "Alice"
        firstCells[1].text() == "alice@example.com"
    }

    def "columns-to-exclude removes the specified columns"() {
        given:
        String yaml = '''
source: datasource1
query: SELECT id, name, email, password FROM users ORDER BY id
columns-to-exclude: ['password']
'''

        when:
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "example.md"))

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        def headers = doc.select("thead th").collect { it.text() }
        !headers*.toUpperCase().contains("PASSWORD")
        headers.containsAll(["id", "name", "email"]*.toUpperCase())
    }

    def "output template (jte) renders the provided template correctly"() {
        given:
        String yaml = '''
source: datasource1
query: SELECT id, name, email, password FROM users ORDER BY id
output:
  template-type: jte
  template: |
    @import java.util.*
    @param List<String>  columns
    @param List<Map<String, Object>> rows
    <h1>Sample output</h1>
    <table>
        <thead>
        <tr>
            @for(var column : columns)
                <th>${column}</th>
            @endfor
        </tr>
        </thead>
        <tbody>
        @for(var row : rows)
           <tr>
                @for(String column : columns)
                    <td>${row.get(column) == null? "": row.get(column).toString()}</td>
                @endfor
            </tr>
        @endfor
        </tbody>
    </table>
'''

        when:
        Optional<HtmlBlock> result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "example.md"))

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        def rows = doc.select("tbody tr")
        rows.size() == 3

        def firstCells = rows[0].select("td")
        firstCells[1].text() == "Alice"
        firstCells[2].text() == "alice@example.com"

        def secondCells = rows[1].select("td")
        secondCells[1].text() == "Bob"
        secondCells[2].text() == "bob@example.com"

        def h1 = doc.select("h1")
        h1.text() == "Sample output"
    }
}