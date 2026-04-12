package uk.anbu.devnotes.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.jte.ContentType
import gg.jte.TemplateEngine
import gg.jte.resolve.DirectoryCodeResolver
import spock.lang.Shared
import spock.lang.Specification
import uk.anbu.devnotes.markdown.code.databasemetadata.DatabaseMetadataConfig
import uk.anbu.devnotes.service.ConfigService

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager

class JdbcDatabaseControllerSpec extends Specification {

    // -------------------------------------------------------------------------
    // Shared H2 database (kept alive for the whole spec)
    // -------------------------------------------------------------------------

    @Shared
    String dbUrl = "jdbc:h2:mem:jdbcdbspec;DB_CLOSE_DELAY=-1"
    @Shared
    String username = "sa"
    @Shared
    String password = ""
    @Shared
    Connection conn

    // Per-test temp directory and controller
    Path tempDir
    JdbcDatabaseController controller

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    def setupSpec() {
        conn = DriverManager.getConnection(dbUrl, username, password)
        conn.createStatement().execute("""
            CREATE TABLE instrument (
                name       VARCHAR(200) NOT NULL,
                type       VARCHAR(20),
                price      DECIMAL(10,2),
                active     BOOLEAN,
                notes      CLOB
            )
        """)
        conn.createStatement().execute("""
            CREATE TABLE product (
                id         INT PRIMARY KEY,
                code       VARCHAR(50)
            )
        """)
    }

    def setup() {
        tempDir = Files.createTempDirectory("jdbcdbctrl")

        ConfigService mockConfig = Mock(ConfigService)
        mockConfig.getDataSourceConfig("testds") >> new ConfigService.DataSourceConfig(
                "testds", dbUrl, username, password)
        mockConfig.getDataSourceConfig(_) >> null
        mockConfig.getDocsDirectory() >> tempDir.toString()
        mockConfig.getDataSources() >> [testds: new ConfigService.DataSourceConfig(
                "testds", dbUrl, username, password)]

        def codeResolver = new DirectoryCodeResolver(Paths.get("src/main/jte"))
        def te = TemplateEngine.create(codeResolver, Paths.get("src/main/jte"), ContentType.Html)

        controller = new JdbcDatabaseController(mockConfig, te)
    }

    def cleanup() {
        tempDir?.toFile()?.deleteDir()
    }

    def cleanupSpec() {
        conn?.close()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Extract the YAML body from each ```database-metadata ... ``` fenced block. */
    private static List<String> extractBlocks(String content) {
        def blocks = []
        def matcher = (content =~ /(?s)```database-metadata\n(.*?)```/)
        while (matcher.find()) {
            blocks << matcher.group(1)
        }
        blocks
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    def "happy path produces a single markdown file with one block per table"() {
        when:
        def response = controller.fetchDatabaseMetadata("testds", "mydb", "PUBLIC", null)

        then:
        response.statusCode.value() == 302

        def mdFile = tempDir.resolve("database/mydb.md").toFile()
        mdFile.exists()

        def blocks = extractBlocks(mdFile.text)
        blocks.size() == 2

        def mapper = new ObjectMapper(new YAMLFactory())
        def configs = blocks.collect { mapper.readValue(it, DatabaseMetadataConfig) }

        configs.every { it.table != null }
        configs.every { it.table.datasource == "testds" }
        configs*.table*.name.toSet() == ["instrument", "product"].toSet()
        configs.every { it.columns != null && it.columns.size() > 0 }
    }

    def "tables are sorted alphabetically in the output file"() {
        when:
        controller.fetchDatabaseMetadata("testds", "sorted", "PUBLIC", null)

        then:
        def blocks = extractBlocks(tempDir.resolve("database/sorted.md").toFile().text)
        def mapper = new ObjectMapper(new YAMLFactory())
        def names = blocks.collect { mapper.readValue(it, DatabaseMetadataConfig).table.name }
        names == names.sort()
    }

    def "table pattern filter produces only matching tables"() {
        when:
        def response = controller.fetchDatabaseMetadata("testds", "filtered", null, "INSTR%")

        then:
        response.statusCode.value() == 302

        def blocks = extractBlocks(tempDir.resolve("database/filtered.md").toFile().text)
        blocks.size() == 1

        def mapper = new ObjectMapper(new YAMLFactory())
        mapper.readValue(blocks[0], DatabaseMetadataConfig).table.name == "instrument"
    }

    def "schema pattern parameter is forwarded to getTables"() {
        when:
        // PUBLIC is the default H2 schema; passing it explicitly should still return tables
        def response = controller.fetchDatabaseMetadata("testds", "schema", "PUBLIC", null)

        then:
        response.statusCode.value() == 302
        def blocks = extractBlocks(tempDir.resolve("database/schema.md").toFile().text)
        blocks.size() == 2
    }

    def "unknown datasource returns 400"() {
        when:
        def response = controller.fetchDatabaseMetadata("nonexistent", "x", null, null)

        then:
        response.statusCode.value() == 400
    }

    def "database/ subdirectory is created when it does not exist"() {
        given:
        def dbDir = tempDir.resolve("database").toFile()
        assert !dbDir.exists()

        when:
        def response = controller.fetchDatabaseMetadata("testds", "newdir", "PUBLIC", null)

        then:
        response.statusCode.value() == 302
        dbDir.exists()
        tempDir.resolve("database/newdir.md").toFile().exists()
    }

    def "each block in the generated file can be deserialized without error"() {
        when:
        controller.fetchDatabaseMetadata("testds", "roundtrip", "PUBLIC", null)

        then:
        def blocks = extractBlocks(tempDir.resolve("database/roundtrip.md").toFile().text)
        def mapper = new ObjectMapper(new YAMLFactory())
        blocks.every { block ->
            def config = mapper.readValue(block, DatabaseMetadataConfig)
            config != null && config.table != null && config.columns != null
        }
    }

    def "h2-type and java-type fields are populated for H2 database columns"() {
        when:
        controller.fetchDatabaseMetadata("testds", "h2types", null, "INSTRUMENT")

        then:
        def blocks = extractBlocks(tempDir.resolve("database/h2types.md").toFile().text)
        blocks.size() == 1
        def mapper = new ObjectMapper(new YAMLFactory())
        def config = mapper.readValue(blocks[0], DatabaseMetadataConfig)

        // Every column must have h2-type and java-type populated
        config.columns.values().every { col ->
            col.h2Type != null && !col.h2Type.isEmpty()
        }
        config.columns.values().every { col ->
            col.javaType != null && !col.javaType.isEmpty()
        }

        // oracle-type and db-type must NOT be present (H2 DB)
        config.columns.values().every { col ->
            col.oracleType == null && col.dbType == null
        }
    }

    def "BOOLEAN column maps to java.lang.Boolean"() {
        when:
        controller.fetchDatabaseMetadata("testds", "booltypes", null, "INSTRUMENT")

        then:
        def blocks = extractBlocks(tempDir.resolve("database/booltypes.md").toFile().text)
        def mapper = new ObjectMapper(new YAMLFactory())
        def config = mapper.readValue(blocks[0], DatabaseMetadataConfig)
        def active = config.columns["active"]
        active != null
        active.javaType == "java.lang.Boolean"
        // H2 returns BOOLEAN - no size suffix expected
        active.h2Type == "BOOLEAN"
    }

    def "CLOB column maps to java.lang.String"() {
        when:
        controller.fetchDatabaseMetadata("testds", "clobtypes", null, "INSTRUMENT")

        then:
        def blocks = extractBlocks(tempDir.resolve("database/clobtypes.md").toFile().text)
        def mapper = new ObjectMapper(new YAMLFactory())
        def config = mapper.readValue(blocks[0], DatabaseMetadataConfig)
        def notes = config.columns["notes"]
        notes != null
        notes.javaType == "java.lang.String"
        // H2 2.x reports CLOB as CHARACTER LARGE OBJECT
        notes.h2Type.toUpperCase().contains("CHARACTER LARGE")
    }

    def "successful fetch redirects to the generated markdown file"() {
        when:
        def response = controller.fetchDatabaseMetadata("testds", "summary", "PUBLIC", null)

        then:
        response.statusCode.value() == 302
        response.headers.getFirst("Location") == "/markdown?filename=database/summary.md"
    }
}

