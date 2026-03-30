package uk.anbu.devnotes.markdown.code

import org.commonmark.node.HtmlBlock
import org.jsoup.Jsoup
import spock.lang.Shared
import spock.lang.Specification

class DatabaseMetadataBlockTranslatorSpec extends Specification {

    @Shared
    DatabaseMetadataBlockTranslator translator

    def setupSpec() {
        translator = new DatabaseMetadataBlockTranslator()
    }

    // -------------------------------------------------------------------------
    // Helper YAML strings
    // -------------------------------------------------------------------------

    static final String FULL_YAML = '''
table:
  name: instrument
  description: Store instruments used in trading.
columns:
  name:
    oracle-type: varchar2(200)
    h2-type: varchar(200)
    java-type: java.lang.String
    description: Instrument name
  type:
    oracle-type: varchar2(20)
    h2-type: varchar(20)
    java-type: java.lang.String
    description: Instrument type
    values:
      PRP: Perpetual bond
      EQU: Equity
  created_at:
    oracle-type: date
    h2-type: timestamp
    java-type: java.time.LocalDateTime
    description: Record creation timestamp
'''

    static final String WITH_DATASOURCE_YAML = '''
table:
  name: instrument
  description: Store instruments
  datasource: myDatasource
columns:
  name:
    oracle-type: varchar2(200)
    h2-type: varchar(200)
    java-type: java.lang.String
    description: Instrument name
'''

    static final String MINIMAL_YAML = '''
table:
  name: minimal_table
columns:
  col1:
    oracle-type: number(10)
'''

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    def "full YAML renders table header and all three columns"() {
        when:
        def result = translator.translate(FULL_YAML)

        then:
        result.isPresent()
        result.get() instanceof HtmlBlock

        def doc = Jsoup.parse(result.get().literal)

        // Title contains table name
        doc.select(".db-meta-table-name").text().contains("instrument")

        // Description is shown
        doc.select(".db-meta-table-desc").text().contains("Store instruments")

        // Header row has 7 columns (DB Type added after H2 Type)
        def headerCells = doc.select("thead th").collect { it.text() }
        headerCells == ["Column", "Oracle Type", "H2 Type", "DB Type", "Java Type", "Description", "Values"]

        // One data row per column in the YAML (3 columns)
        def dataRows = doc.select("tbody tr")
        dataRows.size() == 3

        // Column names appear in the first cell of each row
        def colNames = dataRows.collect { it.select("td.db-meta-col-name").text() }
        colNames.containsAll(["name", "type", "created_at"])
    }

    def "column with values map renders dt/dd pairs"() {
        when:
        def result = translator.translate(FULL_YAML)

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)

        // The 'type' column row should contain a <dl> with PRP and EQU
        def dl = doc.select("dl.db-meta-values")
        dl.size() == 1

        def dtTexts = dl.select("dt").collect { it.text() }
        def ddTexts = dl.select("dd").collect { it.text() }

        dtTexts.containsAll(["PRP", "EQU"])
        ddTexts.containsAll(["Perpetual bond", "Equity"])
    }

    def "missing optional fields render gracefully without NPE"() {
        when:
        def result = translator.translate(MINIMAL_YAML)

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)

        // Table name still shown
        doc.select(".db-meta-table-name").text().contains("minimal_table")

        // No description span rendered (condition was false)
        doc.select(".db-meta-table-desc").isEmpty()

        // Column row exists; empty cells for missing optional fields
        def rows = doc.select("tbody tr")
        rows.size() == 1

        // h2-type, db-type, java-type and description cells should be empty strings
        def cells = rows.first().select("td").collect { it.text() }
        cells[0] == "col1"       // column name
        cells[1] == "number(10)" // oracle-type
        cells[2] == ""           // h2-type (missing)
        cells[3] == ""           // db-type (missing)
        cells[4] == ""           // java-type (missing)
        cells[5] == ""           // description (missing)
    }

    def "datasource key present adds HTMX check form"() {
        when:
        def result = translator.translate(WITH_DATASOURCE_YAML)

        then:
        result.isPresent()
        def html = result.get().literal

        // HTMX attributes are present on the form
        html.contains('hx-post="/database-metadata/check"')
        html.contains('hx-target=')
        html.contains('hx-swap="innerHTML"')

        // Hidden inputs carry the right values
        def doc = Jsoup.parse(html)
        def dsInput = doc.select("input[name=datasource]")
        dsInput.size() == 1
        dsInput.attr("value") == "myDatasource"

        def yamlInput = doc.select("input[name=yamlContent]")
        yamlInput.size() == 1
        yamlInput.attr("value").contains("instrument")

        // The empty diff-result target div is present
        doc.select("div.db-meta-diff-result").size() == 1
    }

    def "no datasource key omits check form and diff div"() {
        when:
        def result = translator.translate(FULL_YAML)

        then:
        result.isPresent()
        def html = result.get().literal

        !html.contains("hx-post")
        !html.contains("db-meta-check-btn")

        def doc = Jsoup.parse(html)
        doc.select("form").isEmpty()
        doc.select("div.db-meta-diff-result").isEmpty()
    }

    def "malformed YAML returns an error div"() {
        given:
        String badYaml = "table: [\ninvalid: yaml: here:"

        when:
        def result = translator.translate(badYaml)

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        doc.select(".database-metadata-error").size() == 1
        doc.select(".database-metadata-error").text().contains("Error parsing")
    }
}

