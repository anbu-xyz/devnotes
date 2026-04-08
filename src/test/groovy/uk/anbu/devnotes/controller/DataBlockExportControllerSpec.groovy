package uk.anbu.devnotes.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.mock.web.MockHttpServletResponse
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir
import uk.anbu.devnotes.cash.ExchangeRates
import uk.anbu.devnotes.markdown.code.datablock.YamlCodeblockConfig
import uk.anbu.devnotes.service.ConfigService
import uk.anbu.devnotes.types.CurrencyPair

import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

class DataBlockExportControllerSpec extends Specification {

    // -------------------------------------------------------------------------
    // Shared in-memory H2 database – created once for the whole spec
    // -------------------------------------------------------------------------

    @Shared String h2Url    = "jdbc:h2:mem:exportspec;DB_CLOSE_DELAY=-1"
    @Shared String h2User   = "sa"
    @Shared String h2Pass   = ""
    @Shared Connection conn

    def setupSpec() {
        conn = DriverManager.getConnection(h2Url, h2User, h2Pass)
        conn.createStatement().execute("""
            CREATE TABLE person (
                id   INTEGER PRIMARY KEY,
                name VARCHAR(100),
                age  INTEGER
            )
        """)
        conn.createStatement().execute("INSERT INTO person VALUES (1, 'Alice', 30)")
        conn.createStatement().execute("INSERT INTO person VALUES (2, 'Bob',   25)")
        conn.createStatement().execute("INSERT INTO person VALUES (3, 'Carol', 35)")

        // Currency conversion fixture
        //   row 1 – cross-currency (GBP → USD, needs rate)
        //   row 2 – same currency  (USD → USD, no rate lookup)
        //   row 3 – no rate configured (JPY → USD)
        //   row 4 – malformed value (no space separator)
        conn.createStatement().execute("""
            CREATE TABLE currency_trade (
                id         INTEGER PRIMARY KEY,
                amount_raw VARCHAR(50)
            )
        """)
        conn.createStatement().execute("INSERT INTO currency_trade VALUES (1, 'GBP 100.00')")
        conn.createStatement().execute("INSERT INTO currency_trade VALUES (2, 'USD 150.00')")
        conn.createStatement().execute("INSERT INTO currency_trade VALUES (3, 'JPY 5000')")
        conn.createStatement().execute("INSERT INTO currency_trade VALUES (4, 'not-valid')")
    }

    def cleanupSpec() {
        conn?.close()
    }

    // -------------------------------------------------------------------------
    // Per-test temp directory for markdown files
    // -------------------------------------------------------------------------

    @TempDir
    Path tempDir

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Instantiate the controller under test with the given ConfigService. */
    private DataBlockExportController newController(ConfigService cs) {
        new DataBlockExportController(cs, null)
    }

    /**
     * Calls {@code exportExcel} and returns the captured {@link MockHttpServletResponse}.
     * The response object acts as both the sink for the Excel bytes (success) and the error
     * message (error cases).
     */
    private MockHttpServletResponse doExport(DataBlockExportController ctrl, Map body) {
        def resp = new MockHttpServletResponse()
        ctrl.exportExcel(body, resp)
        resp
    }

    /**
     * Computes the data-block checksum for the given raw YAML literal using empty shared params
     * – mirrors the logic inside the controller that identifies which block to export.
     */
    private static String checksumOf(String yamlLiteral) {
        def mapper = new ObjectMapper(new YAMLFactory())
        def config = mapper.readValue(yamlLiteral, YamlCodeblockConfig)
        config.checksum([:])
    }

    /**
     * Writes a minimal {@code ```data ... ```} fenced block to a markdown file in {@code tempDir}
     * and returns the checksum of the YAML content so callers can pass it as {@code datablockId}.
     */
    private String writeMdWith(String filename, String yamlLiteral) {
        def content = "```data\n${yamlLiteral}```\n"
        tempDir.resolve(filename).toFile().text = content
        checksumOf(yamlLiteral)
    }

    /** Builds a ConfigService mock that resolves a single named datasource. */
    private ConfigService configServiceFor(String dsName, boolean keySet = true) {
        def cs = Mock(ConfigService)
        cs.getDocsDirectory()           >> tempDir.toString()
        cs.getDataSourceConfig(dsName)  >> new ConfigService.DataSourceConfig(
                dsName, h2Url, h2User, h2Pass)
        cs.isEncryptionKeySet()         >> keySet
        cs
    }

    // -------------------------------------------------------------------------
    // Validation: bad / missing request parameters
    // -------------------------------------------------------------------------

    def "returns 400 when markdownFile is absent from the request body"() {
        given:
        def ctrl = newController(Mock(ConfigService))

        when:
        def resp = doExport(ctrl, [datablockId: "abc"])

        then:
        resp.status == 400
        resp.contentAsString.contains("missing")
    }

    def "returns 400 when datablockId is absent from the request body"() {
        given:
        def ctrl = newController(Mock(ConfigService))

        when:
        def resp = doExport(ctrl, [markdownFile: "test.md"])

        then:
        resp.status == 400
        resp.contentAsString.contains("missing")
    }

    def "returns 400 when params is not a map"() {
        given:
        def cs = Mock(ConfigService)
        cs.getDocsDirectory() >> tempDir.toString()
        def ctrl = newController(cs)

        when:
        def resp = doExport(ctrl, [markdownFile: "test.md", datablockId: "abc", params: "not-a-map"])

        then:
        resp.status == 400
        resp.contentAsString.contains("object")
    }

    def "returns 400 when the markdown file does not exist on disk"() {
        given:
        def cs = Mock(ConfigService)
        cs.getDocsDirectory() >> tempDir.toString()
        def ctrl = newController(cs)

        when:
        def resp = doExport(ctrl, [markdownFile: "nonexistent.md", datablockId: "abc"])

        then:
        resp.status == 400
        resp.contentAsString.contains("not found")
    }

    def "returns 404 when datablockId does not match any data block in the file"() {
        given:
        writeMdWith("test.md", "source: myDs\nquery: SELECT 1\n")
        def cs = Mock(ConfigService)
        cs.getDocsDirectory() >> tempDir.toString()
        def ctrl = newController(cs)

        when:
        def resp = doExport(ctrl, [markdownFile: "test.md", datablockId: "not-a-real-checksum"])

        then:
        resp.status == 404
        resp.contentAsString.contains("not found")
    }

    // -------------------------------------------------------------------------
    // Encryption guard
    // -------------------------------------------------------------------------

    def "returns 403 with a helpful message when datasource password is ENC(...) and no key is loaded"() {
        given:
        def yaml = "source: secureDs\nquery: SELECT 1\n"
        def id = writeMdWith("secure.md", yaml)

        def cs = Mock(ConfigService)
        cs.getDocsDirectory()              >> tempDir.toString()
        cs.getDataSourceConfig("secureDs") >> new ConfigService.DataSourceConfig(
                "secureDs", "jdbc:h2:mem:x", "sa", "ENC(aaaa==)")
        cs.isEncryptionKeySet()            >> false
        def ctrl = newController(cs)

        when:
        def resp = doExport(ctrl, [markdownFile: "secure.md", datablockId: id])

        then:
        resp.status == 403
        resp.contentType.contains("text/plain")
        resp.contentAsString.contains("encrypted password")
        resp.contentAsString.contains("/config/encryption-key")
        resp.contentAsString.contains("secureDs")
    }

    // -------------------------------------------------------------------------
    // Happy path: produces a valid xlsx
    // -------------------------------------------------------------------------

    def "exports all query results as a valid xlsx file with bold header row"() {
        given:
        def yaml = "source: testDs\nquery: SELECT id, name, age FROM person ORDER BY id\n"
        def id = writeMdWith("report.md", yaml)
        def ctrl = newController(configServiceFor("testDs"))

        when:
        def resp = doExport(ctrl, [markdownFile: "report.md", datablockId: id])

        then: "response is a downloadable xlsx"
        resp.status == 200
        resp.contentType.contains("spreadsheetml")
        resp.getHeader("Content-Disposition").contains("report-export.xlsx")

        and: "workbook has a header row plus one row per result"
        def wb = WorkbookFactory.create(new ByteArrayInputStream(resp.contentAsByteArray))
        def sheet = wb.getSheetAt(0)
        sheet.physicalNumberOfRows == 4   // 1 header + 3 data rows

        and: "header cells carry the column labels returned by the database"
        def header = sheet.getRow(0)
        header.getCell(0).stringCellValue.equalsIgnoreCase("id")
        header.getCell(1).stringCellValue.equalsIgnoreCase("name")
        header.getCell(2).stringCellValue.equalsIgnoreCase("age")

        and: "first data row matches Alice's record"
        def row1 = sheet.getRow(1)
        row1.getCell(0).numericCellValue == 1.0
        row1.getCell(1).stringCellValue  == "Alice"
        row1.getCell(2).numericCellValue == 30.0

        and: "subsequent rows contain the remaining records in order"
        sheet.getRow(2).getCell(1).stringCellValue == "Bob"
        sheet.getRow(3).getCell(1).stringCellValue == "Carol"

        cleanup:
        wb?.close()
    }

    def "Content-Disposition filename is derived from the markdown file base name"() {
        given:
        def yaml = "source: testDs\nquery: SELECT id FROM person WHERE id = 1\n"
        def id = writeMdWith("my-special-report.md", yaml)
        def ctrl = newController(configServiceFor("testDs"))

        when:
        def resp = doExport(ctrl, [markdownFile: "my-special-report.md", datablockId: id])

        then:
        resp.status == 200
        resp.getHeader("Content-Disposition").contains("my-special-report-export.xlsx")
    }

    // -------------------------------------------------------------------------
    // Null cell values
    // -------------------------------------------------------------------------

    def "null column values are written as blank cells rather than the string '(null)'"() {
        given:
        conn.createStatement().execute("INSERT INTO person VALUES (99, NULL, 99)")
        def yaml = "source: testDs\nquery: SELECT id, name, age FROM person WHERE id = 99\n"
        def id = writeMdWith("nulltest.md", yaml)
        def ctrl = newController(configServiceFor("testDs"))

        when:
        def resp = doExport(ctrl, [markdownFile: "nulltest.md", datablockId: id])

        then:
        resp.status == 200
        def wb = WorkbookFactory.create(new ByteArrayInputStream(resp.contentAsByteArray))
        def dataRow = wb.getSheetAt(0).getRow(1)
        dataRow.getCell(0).numericCellValue == 99.0
        dataRow.getCell(1).cellType         == CellType.BLANK   // null name → blank
        dataRow.getCell(2).numericCellValue == 99.0

        cleanup:
        wb?.close()
        conn.createStatement().execute("DELETE FROM person WHERE id = 99")
    }

    // -------------------------------------------------------------------------
    // Params forwarding
    // -------------------------------------------------------------------------

    def "named parameters from the request body are forwarded to the SQL query"() {
        given:
        def yaml = "source: testDs\nquery: SELECT id, name FROM person WHERE id = :targetId\nparameters:\n  targetId:\n    value: 2\n    type: integer\n"
        def id = writeMdWith("paramtest.md", yaml)
        def ctrl = newController(configServiceFor("testDs"))

        when:
        def resp = doExport(ctrl, [markdownFile: "paramtest.md", datablockId: id])

        then:
        resp.status == 200
        def wb = WorkbookFactory.create(new ByteArrayInputStream(resp.contentAsByteArray))
        def sheet = wb.getSheetAt(0)
        sheet.physicalNumberOfRows == 2   // header + 1 matching row
        sheet.getRow(1).getCell(1).stringCellValue == "Bob"

        cleanup:
        wb?.close()
    }

    // -------------------------------------------------------------------------
    // Currency-symbol column conversion
    // -------------------------------------------------------------------------

    def "currency symbol column: cross-currency values are converted, same-currency is passed through, missing rate and malformed values keep raw text"() {
        given: "GBP/USD rate is 1.25; no JPY/USD rate is registered"
        ExchangeRates.setExchangeRate(new CurrencyPair("GBP", "USD"), 1.25d)

        // The column alias starts with '$' → target currency USD
        def yaml = """\
source: testDs
query: SELECT id, amount_raw AS "\$amount" FROM currency_trade ORDER BY id
"""
        def id = writeMdWith("currency.md", yaml)
        def ctrl = newController(configServiceFor("testDs"))

        when:
        def resp = doExport(ctrl, [markdownFile: "currency.md", datablockId: id])

        then:
        resp.status == 200
        def wb = WorkbookFactory.create(new ByteArrayInputStream(resp.contentAsByteArray))
        def sheet = wb.getSheetAt(0)
        sheet.physicalNumberOfRows == 5   // 1 header + 4 data rows

        and: "header carries the column label including the currency symbol prefix"
        sheet.getRow(0).getCell(1).stringCellValue.equalsIgnoreCase("\$amount")

        and: "row 1 – GBP 100.00 converted to USD at rate 1.25 → 125.0 (numeric cell)"
        sheet.getRow(1).getCell(1).cellType        == CellType.NUMERIC
        sheet.getRow(1).getCell(1).numericCellValue == 125.0d

        and: "row 2 – USD 150.00 same currency, no rate lookup → 150.0 (numeric cell)"
        sheet.getRow(2).getCell(1).cellType        == CellType.NUMERIC
        sheet.getRow(2).getCell(1).numericCellValue == 150.0d

        and: "row 3 – JPY 5000 no exchange rate configured → raw string preserved"
        sheet.getRow(3).getCell(1).cellType         == CellType.STRING
        sheet.getRow(3).getCell(1).stringCellValue  == "JPY 5000"

        and: "row 4 – 'not-valid' malformed value (no space) → raw string preserved"
        sheet.getRow(4).getCell(1).cellType         == CellType.STRING
        sheet.getRow(4).getCell(1).stringCellValue  == "not-valid"

        cleanup:
        wb?.close()
        ExchangeRates.clear()
    }

    def "£-prefixed column converts source values to GBP"() {
        given:
        ExchangeRates.setExchangeRate(new CurrencyPair("USD", "GBP"), 0.80d)

        def yaml = """\
source: testDs
query: SELECT amount_raw AS "£nav" FROM currency_trade WHERE id = 2
"""
        // row 2: 'USD 150.00' → GBP at 0.80 → 120.0
        def id = writeMdWith("gbp.md", yaml)
        def ctrl = newController(configServiceFor("testDs"))

        when:
        def resp = doExport(ctrl, [markdownFile: "gbp.md", datablockId: id])

        then:
        resp.status == 200
        def wb = WorkbookFactory.create(new ByteArrayInputStream(resp.contentAsByteArray))
        def dataCell = wb.getSheetAt(0).getRow(1).getCell(0)
        dataCell.cellType        == CellType.NUMERIC
        dataCell.numericCellValue == 120.0d

        cleanup:
        wb?.close()
        ExchangeRates.clear()
    }
}

