package uk.anbu.devnotes.controller

import gg.jte.ContentType
import gg.jte.TemplateEngine
import gg.jte.resolve.DirectoryCodeResolver
import org.jsoup.Jsoup
import spock.lang.Shared
import spock.lang.Specification
import uk.anbu.devnotes.service.ConfigService

import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager

class DatabaseMetadataControllerSpec extends Specification {

    // -------------------------------------------------------------------------
    // Shared H2 database and controller
    // -------------------------------------------------------------------------

    @Shared
    String url = "jdbc:h2:mem:dbmetaspec;DB_CLOSE_DELAY=-1"
    @Shared
    String username = "sa"
    @Shared
    String password = ""
    @Shared
    Connection conn
    @Shared
    DatabaseMetadataController controller

    def setupSpec() {
        conn = DriverManager.getConnection(url, username, password)
        conn.createStatement().execute("""
            CREATE TABLE instrument (
                name       VARCHAR(200) NOT NULL,
                type       VARCHAR(20),
                created_at TIMESTAMP
            )
        """)
        conn.createStatement().execute("""
            CREATE TABLE product (
                id         INT PRIMARY KEY,
                code       VARCHAR(50),
                extra_col  VARCHAR(10)
            )
        """)

        // ConfigService stub: "testds" resolves to the shared H2 URL
        ConfigService mockConfig = Mock(ConfigService)
        mockConfig.getDataSourceConfig("testds") >> new ConfigService.DataSourceConfig(
                "testds", url, username, password)
        mockConfig.getDataSourceConfig(_) >> null   // everything else → unknown

        // Real TemplateEngine pointing at src/main/jte (same pattern as SqlExecutionControllerSpec)
        def codeResolver = new DirectoryCodeResolver(Paths.get("src/main/jte"))
        def te = TemplateEngine.create(codeResolver, Paths.get("src/main/jte"), ContentType.Html)

        controller = new DatabaseMetadataController(mockConfig, te)
    }

    def cleanupSpec() {
        conn?.close()
    }

    // -------------------------------------------------------------------------
    // Helper: minimal YAML for the 'instrument' table
    // -------------------------------------------------------------------------

    static String instrumentYaml(String extraColumns = "") {
        """
table:
  name: instrument
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
  created_at:
    oracle-type: date
    h2-type: timestamp
    java-type: java.time.LocalDateTime
    description: Created at
${extraColumns}
"""
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    def "fully-matching table returns match count with no undocumented or ghost sections"() {
        when:
        def html = controller.check("testds", instrumentYaml())
        def doc = Jsoup.parse(html)

        then:
        // Match summary is shown
        doc.select(".db-meta-diff-match").text().contains("3 column(s) match")

        // No undocumented or ghost sections
        doc.select(".db-meta-diff-undocumented").isEmpty()
        doc.select(".db-meta-diff-ghost").isEmpty()
    }

    def "DB column absent from wiki appears as undocumented"() {
        given: "YAML documents only 'name' and 'type', omitting 'created_at'"
        String yaml = """
table:
  name: instrument
columns:
  name:
    h2-type: varchar(200)
  type:
    h2-type: varchar(20)
"""

        when:
        def html = controller.check("testds", yaml)
        def doc = Jsoup.parse(html)

        then:
        // created_at is in the DB but not in the wiki → undocumented
        def undocSection = doc.select(".db-meta-diff-undocumented")
        undocSection.size() == 1
        undocSection.text().contains("created_at")

        // name and type matched
        doc.select(".db-meta-diff-match").text().contains("2 column(s) match")
    }

    def "documented column absent from DB appears as ghost"() {
        given: "YAML documents a 'legacy_code' column that does not exist in the DB"
        String yaml = instrumentYaml("""  legacy_code:
    h2-type: varchar(10)
    description: Old legacy code
""")

        when:
        def html = controller.check("testds", yaml)
        def doc = Jsoup.parse(html)

        then:
        // legacy_code is in the wiki but not in the DB → ghost
        def ghostSection = doc.select(".db-meta-diff-ghost")
        ghostSection.size() == 1
        ghostSection.text().contains("legacy_code")

        // The three real columns still match
        doc.select(".db-meta-diff-match").text().contains("3 column(s) match")
    }

    def "unknown datasource returns error fragment"() {
        when:
        def html = controller.check("nonexistent_ds", instrumentYaml())
        def doc = Jsoup.parse(html)

        then:
        doc.select(".database-metadata-error").size() == 1
        doc.select(".database-metadata-error").text().contains("nonexistent_ds")
    }

    def "table not present in DB returns table-not-found warning"() {
        given:
        String yaml = """
table:
  name: no_such_table
columns:
  col1:
    h2-type: varchar(10)
"""

        when:
        def html = controller.check("testds", yaml)
        def doc = Jsoup.parse(html)

        then:
        // Warning div (not error) is returned
        doc.select(".db-meta-diff-undocumented").size() == 1
        doc.select(".db-meta-diff-undocumented").text().contains("no_such_table")
        doc.select(".database-metadata-error").isEmpty()
    }

    def "malformed YAML returns error fragment"() {
        given:
        String badYaml = "table: [\ninvalid:"

        when:
        def html = controller.check("testds", badYaml)
        def doc = Jsoup.parse(html)

        then:
        doc.select(".database-metadata-error").size() == 1
        doc.select(".database-metadata-error").text().contains("Invalid database-metadata YAML")
    }

    def "diff is case-insensitive for column name matching"() {
        given: "YAML uses lowercase column names; H2 returns them uppercase"
        String yaml = """
table:
  name: product
columns:
  id:
    h2-type: int
  code:
    h2-type: varchar(50)
  extra_col:
    h2-type: varchar(10)
"""

        when:
        def html = controller.check("testds", yaml)
        def doc = Jsoup.parse(html)

        then:
        // All three columns should match despite potential case differences
        doc.select(".db-meta-diff-match").text().contains("3 column(s) match")
        doc.select(".db-meta-diff-undocumented").isEmpty()
        doc.select(".db-meta-diff-ghost").isEmpty()
    }

    def "undocumented columns section includes a copy YAML button with correct snippet"() {
        given: "YAML documents only 'name' and 'type', leaving 'created_at' undocumented"
        String yaml = """
table:
  name: instrument
columns:
  name:
    h2-type: varchar(200)
  type:
    h2-type: varchar(20)
"""

        when:
        def html = controller.check("testds", yaml)
        def doc = Jsoup.parse(html)

        then: "the copy button is present inside the undocumented section"
        def btn = doc.select(".db-meta-diff-undocumented .db-meta-copy-btn")
        btn.size() == 1

        and: "the data-yaml attribute contains the column name"
        def yamlSnippet = btn.first().attr("data-yaml")
        yamlSnippet.contains("created_at:")

        and: "the data-yaml attribute contains the h2-type field (H2 database)"
        yamlSnippet.contains("h2-type:")

        and: "the data-yaml attribute contains a java-type field"
        yamlSnippet.contains("java-type:")
    }

    def "copy button is absent when all columns are matched"() {
        when:
        def html = controller.check("testds", instrumentYaml())
        def doc = Jsoup.parse(html)

        then:
        doc.select(".db-meta-copy-btn").isEmpty()
    }
}
