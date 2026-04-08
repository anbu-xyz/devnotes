package uk.anbu.devnotes.markdown.code

import org.commonmark.node.Node
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Shared
import uk.anbu.devnotes.markdown.code.datablock.ParameterRegistry
import uk.anbu.devnotes.markdown.code.datablock.YamlCodeblockConfig
import uk.anbu.devnotes.service.ConfigService
import uk.anbu.devnotes.service.DatasourceConfigResolver
import uk.anbu.devnotes.types.MarkdownFile
import org.jsoup.Jsoup

import java.nio.file.Files
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
    Connection conn
    @Shared
    def resolver
    @Shared
    ConfigService configService
    @Shared
    DataBlockTranslator translator
    @Shared
    ParameterRegistry registry

    def setupSpec() {
        // create shared in-memory H2 and populate with two users
        conn = DriverManager.getConnection(url, username, password)
        conn.createStatement().execute("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100), email VARCHAR(100), password VARCHAR(100))")
        conn.createStatement().execute("INSERT INTO users (id, name, email, password) VALUES (1, 'Alice', 'alice@example.com', 'secret')")
        conn.createStatement().execute("INSERT INTO users (id, name, email, password) VALUES (2, 'Bob', 'bob@example.com', 'hunter2')")
        conn.createStatement().execute("INSERT INTO users (id, name, email, password) VALUES (3, 'Charlie', 'charlie@example.com', 'goodDay3')")

        conn.createStatement().execute("CREATE TABLE prices (id INT PRIMARY KEY, label VARCHAR(100), amount DECIMAL(14,4), rate DOUBLE)")
        conn.createStatement().execute("INSERT INTO prices (id, label, amount, rate) VALUES (1, 'Widget', 1234567.8900, 0.05678)")

        conn.createStatement().execute("CREATE TABLE events (id INT PRIMARY KEY, label VARCHAR(100), event_date DATE, event_ts TIMESTAMP, amount BIGINT, score DOUBLE, active BOOLEAN)")
        conn.createStatement().execute("INSERT INTO events VALUES (1, 'Alpha', DATE '2025-03-01', TIMESTAMP '2025-03-01 09:00:00', 1000, 3.14, true)")
        conn.createStatement().execute("INSERT INTO events VALUES (2, 'Beta',  DATE '2025-06-15', TIMESTAMP '2025-06-15 18:30:00', 2000, 2.71, false)")

        resolver = ({ String name -> new ConfigService.DataSourceConfig(name, url, username, password) } as DatasourceConfigResolver)
        configService = Mock(ConfigService)
        configService.getSqlMaxRows() >> 100
        registry = new ParameterRegistry()
        translator = new DataBlockTranslator(resolver, configService, registry, null)
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
        def rows = doc.select("tbody tr.data-block-data-row")
        rows.size() == 3
    }

    def "validate that transposed view gets rendered correctly"() {
        given:
        String yaml = '''
source: datasource1
query: SELECT id, name, email, password FROM users ORDER BY id
transpose: true
'''
        when:
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "example.md"))

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        def firstRowHead = doc.select("tbody tr.data-block-data-row th").collect { it.text() }
        firstRowHead == ["ID", "NAME", "EMAIL", "PASSWORD"]
        def topLeft = doc.select("tbody tr.data-block-data-row:first-child th").collect { it.text() }
        topLeft == ["ID"]
        def firstRowData = doc.select("tbody tr.data-block-data-row:first-child td").collect { it.text() }
        firstRowData == ["1", "2", "3"]
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
        def statusRow = doc.select("tfoot tr.data-block-status-row")
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
        def statusRow = doc.select("tfoot tr.data-block-status-row")
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
        Optional<Node> result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "example.md"))

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

    def "parameterized query accepts typed parameters"() {
        given:
        String yaml = '''
source: datasource1
query: SELECT id, name, email FROM users WHERE id = :id
parameters:
  id:
    type: integer
    value: 2
'''
        when:
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "example.md"))

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        def rows = doc.select("tbody tr.data-block-data-row")
        rows.size() == 1
        rows[0].select("td")[1].text() == "Bob"
    }

    def "parameterized query accepts shorthand scalar parameters"() {
        given:
        String yaml = '''
source: datasource1
query: SELECT id, name, email FROM users WHERE id = :id
parameters:
  id: 3
'''
        when:
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "example.md"))

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        def rows = doc.select("tbody tr.data-block-data-row")
        rows.size() == 1
        rows[0].select("td")[1].text() == "Charlie"
    }


    def "parameterized query accepts string"() {
        given:
        String yaml = '''
source: datasource1
query: SELECT id, name, email FROM users WHERE email = :email
parameters:
  email: charlie@example.com
'''
        when:
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "example.md"))

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        def rows = doc.select("tbody tr.data-block-data-row")
        rows.size() == 1
        rows[0].select("td")[1].text() == "Charlie"
    }

    def "parameter block can override data block parameters"() {
        given:
        String paramYaml = '''
id: 3
'''

        String dataYaml = '''
source: datasource1
query: SELECT id, name, email FROM users WHERE id = :id
parameters:
  id: 2
'''

        when:
        registry.clear()
        ParameterBlockTranslator pTranslator = new ParameterBlockTranslator(registry)
        pTranslator.renderParameterBlock(paramYaml)
        def result = translator.renderDataBlock(dataYaml, new MarkdownFile(tempDir, "example.md"))

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        def rows = doc.select("tbody tr.data-block-data-row")
        rows.size() == 1
        rows[0].select("td")[1].text() == "Charlie"
    }

    def "query params override parameter and data block parameters"() {
        given:
        String paramYaml = '''
id: 2
'''

        String dataYaml = '''
source: datasource1
query: SELECT id, name, email FROM users WHERE id = :id
parameters:
  id: 2
'''

        when:
        registry.clear()
        // simulate query parameter overriding everything
        registry.put('id', 3)
        ParameterBlockTranslator pTranslator = new ParameterBlockTranslator(registry)
        pTranslator.renderParameterBlock(paramYaml)
        def result = translator.renderDataBlock(dataYaml, new MarkdownFile(tempDir, "example.md"))

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        def rows = doc.select("tbody tr.data-block-data-row")
        rows.size() == 1
        rows[0].select("td")[1].text() == "Charlie"
    }

    def "column-formats number-format applies custom DecimalFormat pattern to numeric column"() {
        given:
        String yaml = '''
source: datasource1
query: SELECT id, label, amount, rate FROM prices ORDER BY id

column-formats:
  AMOUNT:
    number-format: "#,##0.0000"
  RATE:
    number-format: "0.00000"
'''

        when:
        registry.clear()
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "number-format-test.md"))

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        def cells = doc.select("tbody tr.data-block-data-row td")
        // AMOUNT = 1234567.8900 formatted as "#,##0.0000"
        cells[2].text() == "1,234,567.8900"
        // RATE = 0.05678 formatted as "0.00000"
        cells[3].text() == "0.05678"
    }

    def "column without a column-format still uses the default decimal rendering"() {
        given:
        String yaml = '''
source: datasource1
query: SELECT id, label, amount FROM prices ORDER BY id
'''

        when:
        registry.clear()
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "default-format-test.md"))

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        def cells = doc.select("tbody tr.data-block-data-row td")
        // AMOUNT = 1234567.89 rendered with default %,.2f
        cells[2].text() == "1,234,567.89"
    }

    def "column-formats number-format is matched case-insensitively"() {
        given:
        // YAML key in lowercase, H2 returns column names in uppercase
        String yaml = '''
source: datasource1
query: SELECT id, label, amount FROM prices ORDER BY id

column-formats:
  amount:
    number-format: "#,##0.00"
'''

        when:
        registry.clear()
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "case-insensitive-format-test.md"))

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        def cells = doc.select("tbody tr.data-block-data-row td")
        cells[2].text() == "1,234,567.89"
    }

    def "returns enc-key-needed warning when datasource password is encrypted and key is not set"() {
        given:
        def mockConfigService = Mock(ConfigService)
        mockConfigService.isEncryptionKeySet() >> false
        mockConfigService.getSqlMaxRows() >> 100
        def ds = new ConfigService.DataSourceConfig("myDs", "jdbc:h2:mem:", "sa", "ENC(abc123==)")
        def encResolver = { String name -> ds } as DatasourceConfigResolver
        def encTranslator = new DataBlockTranslator(encResolver, mockConfigService, null, null)
        def yaml = '''
source: myDs
query: SELECT 1
'''

        when:
        def result = encTranslator.renderDataBlock(yaml, null)

        then:
        result.isPresent()
        result.get() instanceof org.commonmark.node.HtmlBlock
        (result.get() as org.commonmark.node.HtmlBlock).literal.contains("enc-key-needed")
        (result.get() as org.commonmark.node.HtmlBlock).literal.contains("/config/encryption-key")
        (result.get() as org.commonmark.node.HtmlBlock).literal.contains("myDs")
    }

    // =========================================================================
    // Error paths
    // =========================================================================

    def "malformed YAML input returns error block"() {
        given:
        String yaml = "this: {is: :bad: yaml"

        when:
        def result = translator.renderDataBlock(yaml, null)

        then:
        result.isPresent()
        result.get().literal.contains("Error parsing data block YAML")
    }

    def "missing source returns error block"() {
        given:
        String yaml = "query: SELECT 1"

        when:
        registry.clear()
        def result = translator.renderDataBlock(yaml, null)

        then:
        result.isPresent()
        result.get().literal.contains("source")
    }

    def "unknown datasource returns error block"() {
        given:
        String yaml = '''
source: nonExistentDs
query: SELECT 1
'''
        def nullResolver = { String name -> null } as DatasourceConfigResolver
        def t = new DataBlockTranslator(nullResolver, configService, new ParameterRegistry(), null)

        when:
        def result = t.renderDataBlock(yaml, null)

        then:
        result.isPresent()
        result.get().literal.contains("nonExistentDs")
        result.get().literal.contains("not configured")
    }

    def "missing query returns error block"() {
        given:
        String yaml = "source: datasource1"

        when:
        registry.clear()
        def result = translator.renderDataBlock(yaml, null)

        then:
        result.isPresent()
        result.get().literal.contains("query")
    }

    def "SQL execution error returns error block with retry button"() {
        given:
        String yaml = '''
source: datasource1
query: SELECT * FROM nonexistent_table_xyz
'''

        when:
        registry.clear()
        def result = translator.renderDataBlock(yaml, null)

        then:
        result.isPresent()
        result.get().literal.contains("data-block-sql-error")
        result.get().literal.contains("Retry")
        result.get().literal.contains("nonexistent_table_xyz")
    }

    // =========================================================================
    // combine-single-column
    // =========================================================================

    def "single-column result is combined into one cell by default"() {
        given:
        String yaml = '''
source: datasource1
query: SELECT name FROM users ORDER BY id
'''

        when:
        registry.clear()
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "single-col.md"))

        then:
        result.isPresent()
        def literal = result.get().literal
        literal.contains("data-block-combined")
        literal.contains("Alice")
        literal.contains("Bob")
        literal.contains("Charlie")
    }

    def "combine-single-column false renders a normal table for a single column"() {
        given:
        String yaml = '''
source: datasource1
query: SELECT name FROM users ORDER BY id
combine-single-column: false
'''

        when:
        registry.clear()
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "single-col-false.md"))

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        doc.select("tbody tr.data-block-data-row").size() == 3
        !result.get().literal.contains("data-block-combined")
    }

    // =========================================================================
    // source with "/" path prefix
    // =========================================================================

    def "source with path prefix extracts the datasource name correctly"() {
        given:
        String yaml = '''
source: database/datasource1
query: SELECT id, name FROM users ORDER BY id
'''

        when:
        registry.clear()
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "path-source.md"))

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        doc.select("tbody tr.data-block-data-row").size() == 3
    }

    // =========================================================================
    // hide-row-count: true
    // =========================================================================

    def "hide-row-count true suppresses row count even when threshold is exceeded"() {
        given:
        String yaml = '''
source: datasource1
query: SELECT * FROM users
hide-row-count: true
hide-row-count-when-less-than: 1
'''

        when:
        registry.clear()
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "hide-count.md"))

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        doc.select("tfoot tr.data-block-status-row").size() == 0
    }

    // =========================================================================
    // transposed view with max-rows reached
    // =========================================================================

    def "transposed view shows max-limit footer when row limit is reached"() {
        given:
        String yaml = '''
source: datasource1
query: SELECT id, name FROM users ORDER BY id
transpose: true
options:
  row-limit: 2
'''

        when:
        registry.clear()
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "transposed-limit.md"))

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        def statusRow = doc.select("tr.data-block-status-row")
        statusRow.size() == 1
        statusRow.first().text().contains("max limit reached")
    }

    // =========================================================================
    // Cache hit
    // =========================================================================

    def "pre-existing cache file is returned without re-executing the query"() {
        given:
        def markdownFile = new MarkdownFile(tempDir, "cache-hit-test.md")
        String yaml = '''
source: datasource1
query: SELECT id FROM users WHERE id = 1
'''
        // Render once to populate the cache
        translator.renderDataBlock(yaml, markdownFile)

        // Overwrite the .output file with custom HTML
        String customHtml = "<div class='from-cache'>CACHED_CONTENT</div>"
        def cacheFile = Files.list(tempDir)
                .filter { it.toString().endsWith(".output") }
                .findFirst()
                .get()
        Files.writeString(cacheFile, customHtml)

        when:
        def result = translator.renderDataBlock(yaml, markdownFile)

        then:
        result.isPresent()
        result.get().literal == customHtml
    }

    // =========================================================================
    // renderDataBlockFreshFromYaml
    // =========================================================================

    def "renderDataBlockFreshFromYaml renders query result"() {
        given:
        String yaml = '''
source: datasource1
query: SELECT id, name FROM users WHERE id = 1
'''

        when:
        registry.clear()
        def result = translator.renderDataBlockFreshFromYaml(yaml, null, [:])

        then:
        result.isPresent()
        result.get().literal.contains("Alice")
    }

    def "renderDataBlockFreshFromYaml with bad YAML returns error block"() {
        given:
        String yaml = "this: {is: :bad"

        when:
        def result = translator.renderDataBlockFreshFromYaml(yaml, null, [:])

        then:
        result.isPresent()
        result.get().literal.contains("Error parsing data block YAML for refresh")
    }

    // =========================================================================
    // executeQueryForExport
    // =========================================================================

    def "executeQueryForExport streams all rows without row limit"() {
        given:
        def config = new YamlCodeblockConfig()
        config.source = "datasource1"
        config.query = "SELECT id, name FROM users ORDER BY id"

        when:
        registry.clear()
        def names = translator.executeQueryForExport(config, [:]) { rs ->
            def list = []
            while (rs.next()) { list << rs.getString("name") }
            list
        }

        then:
        names == ["Alice", "Bob", "Charlie"]
    }

    def "executeQueryForExport throws IllegalArgumentException for missing source"() {
        given:
        def config = new YamlCodeblockConfig()

        when:
        translator.executeQueryForExport(config, [:]) { rs -> null }

        then:
        thrown(IllegalArgumentException)
    }

    def "executeQueryForExport throws IllegalArgumentException for unknown datasource"() {
        given:
        def config = new YamlCodeblockConfig()
        config.source = "unknown_ds"
        def t = new DataBlockTranslator({ String name -> null } as DatasourceConfigResolver,
                configService, new ParameterRegistry(), null)

        when:
        t.executeQueryForExport(config, [:]) { rs -> null }

        then:
        thrown(IllegalArgumentException)
    }

    // =========================================================================
    // Parameter type coercions
    // =========================================================================

    def "long parameter type filters correctly"() {
        given:
        String yaml = '''
source: datasource1
query: SELECT id, label FROM events WHERE amount = :val
parameters:
  val:
    type: long
    value: 1000
'''

        when:
        registry.clear()
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "long-param.md"))

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        def rows = doc.select("tbody tr.data-block-data-row")
        rows.size() == 1
        rows[0].select("td")[1].text() == "Alpha"
    }

    def "double parameter type filters correctly"() {
        given:
        String yaml = '''
source: datasource1
query: SELECT id, label FROM events WHERE score > :val
parameters:
  val:
    type: double
    value: 3.0
'''

        when:
        registry.clear()
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "double-param.md"))

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        def rows = doc.select("tbody tr.data-block-data-row")
        rows.size() == 1
        rows[0].select("td")[1].text() == "Alpha"
    }

    def "decimal parameter type filters correctly"() {
        given:
        String yaml = '''
source: datasource1
query: SELECT id, label FROM events WHERE amount >= :val
parameters:
  val:
    type: decimal
    value: 2000
'''

        when:
        registry.clear()
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "decimal-param.md"))

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        def rows = doc.select("tbody tr.data-block-data-row")
        rows.size() == 1
        rows[0].select("td")[1].text() == "Beta"
    }

    def "boolean parameter type filters correctly"() {
        given:
        String yaml = '''
source: datasource1
query: SELECT id, label FROM events WHERE active = :flag
parameters:
  flag:
    type: boolean
    value: true
'''

        when:
        registry.clear()
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "bool-param.md"))

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        def rows = doc.select("tbody tr.data-block-data-row")
        rows.size() == 1
        rows[0].select("td")[1].text() == "Alpha"
    }

    def "date parameter type filters correctly"() {
        given:
        String yaml = '''
source: datasource1
query: SELECT id, label FROM events WHERE event_date = :dt
parameters:
  dt:
    type: date
    value: "2025-03-01"
'''

        when:
        registry.clear()
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "date-param.md"))

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        def rows = doc.select("tbody tr.data-block-data-row")
        rows.size() == 1
        rows[0].select("td")[1].text() == "Alpha"
    }

    def "timestamp parameter type (LocalDateTime format) filters correctly"() {
        given:
        String yaml = '''
source: datasource1
query: SELECT id, label FROM events WHERE event_ts = :ts
parameters:
  ts:
    type: timestamp
    value: "2025-06-15T18:30:00"
'''

        when:
        registry.clear()
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "ts-param.md"))

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        def rows = doc.select("tbody tr.data-block-data-row")
        rows.size() == 1
        rows[0].select("td")[1].text() == "Beta"
    }

    def "timestamp parameter type (LocalDate-as-midnight format) filters correctly"() {
        given:
        // event_ts for id=1 is '2025-03-01 09:00:00'; midnight won't match it,
        // so we use a range query: event_ts >= :ts AND event_ts < :ts2
        String yaml = '''
source: datasource1
query: SELECT id, label FROM events WHERE event_ts >= :ts AND event_ts < :ts2
parameters:
  ts:
    type: timestamp
    value: "2025-03-01"
  ts2:
    type: timestamp
    value: "2025-03-02"
'''

        when:
        registry.clear()
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "ts-date-param.md"))

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        def rows = doc.select("tbody tr.data-block-data-row")
        rows.size() == 1
        rows[0].select("td")[1].text() == "Alpha"
    }

    def "string parameter type filters correctly"() {
        given:
        String yaml = '''
source: datasource1
query: SELECT id, label FROM events WHERE label = :lbl
parameters:
  lbl:
    type: string
    value: "Beta"
'''

        when:
        registry.clear()
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "string-param.md"))

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        def rows = doc.select("tbody tr.data-block-data-row")
        rows.size() == 1
        rows[0].select("td")[1].text() == "Beta"
    }

    // =========================================================================
    // enc-key-needed returnTo URL includes markdown file path
    // =========================================================================

    def "enc-key-needed block includes returnTo URL encoding the markdown filename"() {
        given:
        def mockConfigService = Mock(ConfigService)
        mockConfigService.isEncryptionKeySet() >> false
        mockConfigService.getSqlMaxRows() >> 100
        def ds = new ConfigService.DataSourceConfig("secured", "jdbc:h2:mem:", "sa", "ENC(xyz==)")
        def encResolver = { String name -> ds } as DatasourceConfigResolver
        def encTranslator = new DataBlockTranslator(encResolver, mockConfigService, null, null)
        String yaml = '''
source: secured
query: SELECT 1
'''

        when:
        def result = encTranslator.renderDataBlock(yaml, new MarkdownFile(tempDir, "my-notes.md"))

        then:
        result.isPresent()
        def literal = result.get().literal
        literal.contains("enc-key-needed")
        literal.contains("returnTo=")
        literal.contains("my-notes.md")
    }
}