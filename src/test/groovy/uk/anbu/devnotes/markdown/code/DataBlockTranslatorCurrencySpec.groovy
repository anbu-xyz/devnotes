package uk.anbu.devnotes.markdown.code

import org.jsoup.Jsoup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir
import uk.anbu.devnotes.cash.ExchangeRates
import uk.anbu.devnotes.markdown.code.datablock.ParameterRegistry
import uk.anbu.devnotes.service.ConfigService
import uk.anbu.devnotes.service.DatasourceConfigResolver
import uk.anbu.devnotes.service.ExchangeRateService
import uk.anbu.devnotes.types.CurrencyPair
import uk.anbu.devnotes.types.MarkdownFile

import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

class DataBlockTranslatorCurrencySpec extends Specification {

    @TempDir
    Path tempDir

    @Shared
    String url = "jdbc:h2:mem:currencyspec;DB_CLOSE_DELAY=-1"
    @Shared
    String username = "sa"
    @Shared
    String password = ""
    @Shared
    Connection conn

    DataBlockTranslator translator
    ExchangeRateService exchangeRateService

    def setupSpec() {
        conn = DriverManager.getConnection(url, username, password)
        // portfolio: dollar_val, pound_val, euro_val use wire format "CCC amount"
        conn.createStatement().execute("""
            CREATE TABLE portfolio (
                id        INT PRIMARY KEY,
                name      VARCHAR(100),
                dollar_val  VARCHAR(20),
                pound_val   VARCHAR(20),
                euro_val    VARCHAR(20),
                plain_val   DOUBLE
            )
        """)
        conn.createStatement().execute("""
            INSERT INTO portfolio VALUES
              (1, 'Alpha', 'GBP 200', 'USD 300',   'USD 1000', 9.99),
              (2, 'Beta',  'EUR 500', 'GBP 150',   'EUR 2500', 42.0),
              (3, 'Gamma', 'JPY 1000','USD 100',   'GBP 750',  1.5)
        """)
        // fx_amounts: includes a null row and a malformed row
        conn.createStatement().execute("""
            CREATE TABLE fx_amounts (
                id    INT PRIMARY KEY,
                val   VARCHAR(20)
            )
        """)
        conn.createStatement().execute("""
            INSERT INTO fx_amounts VALUES (1, 'GBP 100'), (2, 'GBP -50'), (3, NULL), (4, 'BADVAL')
        """)
    }

    def setup() {
        ExchangeRates.clear()

        def configService = Mock(ConfigService)
        configService.getDocsDirectory() >> tempDir.toString()
        exchangeRateService = new ExchangeRateService(configService)
        exchangeRateService.init()

        def dsResolver = { String name ->
            new ConfigService.DataSourceConfig(name, url, username, password)
        } as DatasourceConfigResolver

        def cs = Mock(ConfigService)
        cs.getSqlMaxRows() >> 100

        translator = new DataBlockTranslator(dsResolver, cs, new ParameterRegistry(), exchangeRateService)
    }

    def cleanupSpec() {
        conn?.close()
    }

    // =========================================================================
    // resolveTargetCurrency static helper
    // =========================================================================

    def "resolveTargetCurrency maps dollar prefix to USD"() {
        expect:
        DataBlockTranslator.resolveTargetCurrency('$balance').get() == "USD"
    }

    def "resolveTargetCurrency maps pound prefix to GBP"() {
        expect:
        DataBlockTranslator.resolveTargetCurrency('\u00A3nav').get() == "GBP"
    }

    def "resolveTargetCurrency maps euro prefix to EUR"() {
        expect:
        DataBlockTranslator.resolveTargetCurrency('\u20ACrevenue').get() == "EUR"
    }

    def "resolveTargetCurrency returns empty for plain column name"() {
        expect:
        !DataBlockTranslator.resolveTargetCurrency('balance').isPresent()
        !DataBlockTranslator.resolveTargetCurrency('amount').isPresent()
        !DataBlockTranslator.resolveTargetCurrency('').isPresent()
        !DataBlockTranslator.resolveTargetCurrency(null).isPresent()
    }

    // =========================================================================
    // Dollar-column ($) → USD conversion
    // =========================================================================

    def "dollar-column converts GBP value to USD"() {
        given:
        ExchangeRates.setExchangeRate(new CurrencyPair("GBP", "USD"), 1.27)
        // combine-single-column: false so createTdTag is called (not combineIfSingleColumn)
        String yaml = """\
source: ds
combine-single-column: false
query: SELECT dollar_val AS "\$val" FROM portfolio WHERE id = 1
"""
        when:
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "test.md"))

        then:
        result.isPresent()
        def td = Jsoup.parse(result.get().literal).select("tbody td")[0]
        td.hasClass("data-block-number")
        td.text() == "254.00"   // 200 GBP * 1.27
    }

    def "dollar-column converts EUR value to USD"() {
        given:
        ExchangeRates.setExchangeRate(new CurrencyPair("EUR", "USD"), 1.085)
        String yaml = """\
source: ds
combine-single-column: false
query: SELECT dollar_val AS "\$val" FROM portfolio WHERE id = 2
"""
        when:
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "test.md"))

        then:
        result.isPresent()
        def td = Jsoup.parse(result.get().literal).select("tbody td")[0]
        td.hasClass("data-block-number")
        td.text() == "542.50"   // 500 EUR * 1.085
    }

    // =========================================================================
    // Pound-column (£) → GBP conversion
    // =========================================================================

    def "pound-column converts USD value to GBP"() {
        given:
        ExchangeRates.setExchangeRate(new CurrencyPair("USD", "GBP"), 0.787)
        String yaml = """\
source: ds
combine-single-column: false
query: SELECT pound_val AS "\u00A3val" FROM portfolio WHERE id = 1
"""
        when:
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "test.md"))

        then:
        result.isPresent()
        def td = Jsoup.parse(result.get().literal).select("tbody td")[0]
        td.hasClass("data-block-number")
        // 300 USD * 0.787 = 236.10
        td.text() == "236.10"
    }

    // =========================================================================
    // Euro-column (€) → EUR conversion via cross-rate
    // =========================================================================

    def "euro-column converts GBP value to EUR via cross-rate"() {
        given:
        // Cross-rate: GBP/EUR derived from GBP/USD and EUR/USD
        ExchangeRates.setExchangeRate(new CurrencyPair("GBP", "USD"), 1.27)
        ExchangeRates.setExchangeRate(new CurrencyPair("EUR", "USD"), 1.085)
        String yaml = """\
source: ds
combine-single-column: false
query: SELECT euro_val AS "\u20ACval" FROM portfolio WHERE id = 3
"""
        when:
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "test.md"))

        then:
        result.isPresent()
        def td = Jsoup.parse(result.get().literal).select("tbody td")[0]
        // euro_val for row 3 is 'GBP 750'; cross-rate GBP/EUR ≈ 1.27/1.085
        // 750 * (1.27/1.085) ≈ 877.88
        td.hasClass("data-block-number")
        Math.abs(td.text().replaceAll(",", "").toDouble() - 750 * 1.27 / 1.085) < 0.02
    }

    // =========================================================================
    // Same currency — no conversion
    // =========================================================================

    def "column value already in target currency is formatted without conversion"() {
        given: "dollar column with a USD value — no rate lookup needed"
        String yaml = """\
source: ds
combine-single-column: false
query: SELECT 'USD 500' AS "\$val" FROM portfolio WHERE id = 1
"""
        when:
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "test.md"))

        then:
        result.isPresent()
        def td = Jsoup.parse(result.get().literal).select("tbody td")[0]
        td.hasClass("data-block-number")
        td.text() == "500.00"
    }

    // =========================================================================
    // Unknown rate — amber no-rate cell
    // =========================================================================

    def "unknown rate shows raw value with data-block-no-rate CSS class"() {
        given: "no rates configured"
        String yaml = """\
source: ds
combine-single-column: false
query: SELECT 'GBP 200' AS "\$val" FROM portfolio WHERE id = 1
"""
        when:
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "test.md"))

        then:
        result.isPresent()
        def td = Jsoup.parse(result.get().literal).select("tbody td")[0]
        td.hasClass("data-block-no-rate")
        td.text() == "GBP 200"
        td.attr("title").contains("GBP")
        td.attr("title").contains("USD")
    }

    // =========================================================================
    // Null value
    // =========================================================================

    def "null value in currency column renders as (null) without conversion"() {
        given: "select all fx_amounts rows; id=3 has null val — column won't be stripped"
        ExchangeRates.setExchangeRate(new CurrencyPair("GBP", "USD"), 1.27)
        String yaml = """\
source: ds
combine-single-column: false
query: SELECT val AS "\$val" FROM fx_amounts ORDER BY id
"""
        when:
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "test.md"))

        then:
        result.isPresent()
        def tds = Jsoup.parse(result.get().literal).select("tbody td")
        // Row order: GBP 100, GBP -50, NULL, BADVAL
        tds[2].text() == "(null)"
        !tds[2].hasClass("data-block-number")
        !tds[2].hasClass("data-block-no-rate")
        !tds[2].hasClass("data-block-currency-error")
    }

    // =========================================================================
    // Negative amount
    // =========================================================================

    def "negative amount converts correctly"() {
        given:
        ExchangeRates.setExchangeRate(new CurrencyPair("GBP", "USD"), 1.27)
        // fx_amounts id=2: val='GBP -50' — query all rows so column isn't cleaned
        String yaml = """\
source: ds
combine-single-column: false
query: SELECT val AS "\$val" FROM fx_amounts ORDER BY id
"""
        when:
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "test.md"))

        then:
        result.isPresent()
        def tds = Jsoup.parse(result.get().literal).select("tbody td")
        // Row 2 (index 1): GBP -50 * 1.27 = -63.50
        tds[1].hasClass("data-block-number")
        tds[1].text() == "-63.50"
    }

    // =========================================================================
    // Malformed value
    // =========================================================================

    def "malformed value falls back to error cell with data-block-currency-error class"() {
        given:
        ExchangeRates.setExchangeRate(new CurrencyPair("GBP", "USD"), 1.27)
        // fx_amounts id=4: val='BADVAL' — query all rows
        String yaml = """\
source: ds
combine-single-column: false
query: SELECT val AS "\$val" FROM fx_amounts ORDER BY id
"""
        when:
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "test.md"))

        then:
        result.isPresent()
        def tds = Jsoup.parse(result.get().literal).select("tbody td")
        // Row 4 (index 3): 'BADVAL' — no space → error cell
        tds[3].hasClass("data-block-currency-error")
        tds[3].text() == "BADVAL"
    }

    // =========================================================================
    // Non-prefixed column — unaffected
    // =========================================================================

    def "non-prefixed column is unaffected by currency conversion"() {
        given:
        String yaml = """\
source: ds
query: SELECT name FROM portfolio WHERE id = 1
"""
        when:
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "test.md"))

        then:
        result.isPresent()
        // name column → combineIfSingleColumn renders as combined cell (no td class)
        def body = result.get().literal
        body.contains("Alpha")
        !body.contains("data-block-number")
        !body.contains("data-block-currency-error")
        !body.contains("data-block-no-rate")
    }

    // =========================================================================
    // column-formats number-format applied to converted result
    // =========================================================================

    def "column-formats number-format is applied to converted currency value"() {
        given:
        ExchangeRates.setExchangeRate(new CurrencyPair("GBP", "USD"), 1.27)
        String yaml = """\
source: ds
combine-single-column: false
query: SELECT 'GBP 1000' AS "\$val" FROM portfolio WHERE id = 1
column-formats:
  "\$val":
    number-format: "#,##0"
"""
        when:
        def result = translator.renderDataBlock(yaml, new MarkdownFile(tempDir, "test.md"))

        then:
        result.isPresent()
        def td = Jsoup.parse(result.get().literal).select("tbody td")[0]
        // 1000 * 1.27 = 1270, formatted as "#,##0" → "1,270"
        td.hasClass("data-block-number")
        td.text() == "1,270"
    }
}

