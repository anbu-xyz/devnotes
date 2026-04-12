package uk.anbu.devnotes.controller

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import spock.lang.Specification
import spock.lang.TempDir
import uk.anbu.devnotes.service.ConfigService
import uk.anbu.devnotes.service.ExchangeRateService

import java.nio.file.Files
import java.nio.file.Path

class DataBlockRefreshControllerSpec extends Specification {

    @TempDir
    Path tempDir

    def "returns enc-key-needed warning HTML when datasource password is encrypted and no key is set"() {
        given:
        def configService = Mock(ConfigService)
        configService.isEncryptionKeySet() >> false
        configService.getDocsDirectory() >> tempDir.toString()
        configService.getDataSourceConfig("myDs") >> new ConfigService.DataSourceConfig(
                "myDs", "jdbc:h2:mem:", "sa", "ENC(abc123==)")

        // Write a minimal markdown file with a data block
        def mdFile = tempDir.resolve("test.md")
        mdFile.toFile().text = '''\
```data
source: myDs
query: SELECT 1
```
'''

        def controller = new DataBlockRefreshController(configService, null)

        def body = [
                markdownFile: "test.md",
                datablockId : computeEditChecksum("source: myDs\nquery: SELECT 1\n", 0L),
                params      : [__exchangeRatesVersion: 0L]
        ]

        when:
        def response = controller.renderFragment(body)

        then:
        response.statusCode == HttpStatus.OK
        response.headers.getContentType().isCompatibleWith(MediaType.TEXT_HTML)
        response.body.contains("enc-key-needed")
        response.body.contains("/config/encryption-key")
        response.body.contains("myDs")
    }

    def "fragment checksum ignores filename and injects exchange-rate version when missing"() {
        given:
        def configService = Mock(ConfigService)
        configService.isEncryptionKeySet() >> false
        configService.getDocsDirectory() >> tempDir.toString()
        configService.getDataSourceConfig("myDs") >> new ConfigService.DataSourceConfig(
                "myDs", "jdbc:h2:mem:", "sa", "ENC(abc123==)")
        def exchangeRateService = Mock(ExchangeRateService)
        exchangeRateService.getVersion() >> 42L

        def mdFile = tempDir.resolve("test.md")
        mdFile.toFile().text = '''\
```data
source: myDs
query: SELECT 1
```
'''

        def controller = new DataBlockRefreshController(configService, exchangeRateService)

        when:
        def response = controller.renderFragment([
                markdownFile: "test.md",
                datablockId : computeEditChecksum("source: myDs\nquery: SELECT 1\n", 42L),
                params      : [filename: "test.md"]
        ])

        then:
        response.statusCode == HttpStatus.OK
        response.headers.getContentType().isCompatibleWith(MediaType.TEXT_HTML)
        response.body.contains("enc-key-needed")
    }

    def "returns 400 when markdownFile is missing"() {
        given:
        def configService = Mock(ConfigService)
        def controller = new DataBlockRefreshController(configService, null)

        when:
        def response = controller.renderFragment([datablockId: "abc"])

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
    }

    def "returns 400 when datablockId is missing"() {
        given:
        def configService = Mock(ConfigService)
        def controller = new DataBlockRefreshController(configService, null)

        when:
        def response = controller.renderFragment([markdownFile: "test.md"])

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
    }

    // =========================================================================
    // POST /datablock/edit
    // =========================================================================

    def "edit - returns 400 when markdownFile is missing"() {
        given:
        def controller = new DataBlockRefreshController(Mock(ConfigService), null)

        when:
        def response = controller.editBlock([blockId: "abc", newContent: "source: ds\nquery: SELECT 1\n"])

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
    }

    def "edit - returns 400 when blockId is missing"() {
        given:
        def controller = new DataBlockRefreshController(Mock(ConfigService), null)

        when:
        def response = controller.editBlock([markdownFile: "test.md", newContent: "source: ds\nquery: SELECT 1\n"])

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
    }

    def "edit - returns 400 when newContent is missing"() {
        given:
        def controller = new DataBlockRefreshController(Mock(ConfigService), null)

        when:
        def response = controller.editBlock([markdownFile: "test.md", blockId: "abc"])

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
    }

    def "edit - returns 400 when the markdown file does not exist on disk"() {
        given:
        def configService = Mock(ConfigService)
        configService.getDocsDirectory() >> tempDir.toString()
        def controller = new DataBlockRefreshController(configService, null)

        when:
        def response = controller.editBlock([
                markdownFile: "nonexistent.md",
                blockId     : "abc",
                newContent  : "source: ds\nquery: SELECT 1\n"
        ])

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
        response.body.contains("not found")
    }

    def "edit - returns 409 when file was modified after the client timestamp"() {
        given:
        def configService = Mock(ConfigService)
        configService.getDocsDirectory() >> tempDir.toString()
        def controller = new DataBlockRefreshController(configService, null)
        tempDir.resolve("test.md").toFile().text = "# hello\n"

        when:
        def response = controller.editBlock([
                markdownFile    : "test.md",
                blockId         : "abc",
                newContent      : "source: ds\nquery: SELECT 1\n",
                fileLastModified: "2020-01-01 00:00:00"
        ])

        then:
        response.statusCode == HttpStatus.CONFLICT
    }

    def "edit - returns 404 when blockId does not match any data block in the file"() {
        given:
        def configService = Mock(ConfigService)
        configService.getDocsDirectory() >> tempDir.toString()
        def exchangeRateService = Mock(ExchangeRateService)
        exchangeRateService.getVersion() >> 0L
        def controller = new DataBlockRefreshController(configService, exchangeRateService)
        tempDir.resolve("test.md").toFile().text = '```data\nsource: ds\nquery: SELECT 1\n```\n'

        when:
        def response = controller.editBlock([
                markdownFile: "test.md",
                blockId     : "doesnotmatch",
                newContent  : "source: ds\nquery: SELECT 2\n"
        ])

        then:
        response.statusCode == HttpStatus.NOT_FOUND
    }

    def "edit - updates markdown file on disk, removes old cache, and returns 200 HTML with last-modified header"() {
        given:
        def dbName = "editHappyDb_${System.nanoTime()}"
        def url = "jdbc:h2:mem:${dbName};DB_CLOSE_DELAY=-1"
        def conn = java.sql.DriverManager.getConnection(url, "sa", "")
        conn.createStatement().execute("CREATE TABLE items (id INT, label VARCHAR(100))")
        conn.createStatement().execute("INSERT INTO items VALUES (1, 'Alpha')")

        def configService = Mock(ConfigService)
        configService.getDocsDirectory() >> tempDir.toString()
        configService.getSqlMaxRows() >> 100
        configService.getDataSourceConfig("editDs") >> new ConfigService.DataSourceConfig("editDs", url, "sa", "")
        def exchangeRateService = Mock(ExchangeRateService)
        exchangeRateService.getVersion() >> 0L
        def controller = new DataBlockRefreshController(configService, exchangeRateService)

        def literal = "source: editDs\nquery: SELECT id, label FROM items\n"
        def blockId = computeEditChecksum(literal)
        def mdFile = tempDir.resolve("page.md")
        mdFile.toFile().text = "```data\n${literal}```\n"

        // Pre-existing stale cache
        def cacheFile = tempDir.resolve("page.${blockId}.output")
        cacheFile.toFile().text = "<table>stale</table>"

        def newLiteral = "source: editDs\nquery: SELECT id, label FROM items ORDER BY id\n"

        when:
        def response = controller.editBlock([
                markdownFile: "page.md",
                blockId     : blockId,
                newContent  : newLiteral
        ])

        then:
        response.statusCode == HttpStatus.OK
        response.headers.getContentType().isCompatibleWith(MediaType.TEXT_HTML)
        response.body.contains("<table")
        response.body.contains("Alpha")
        response.headers.getFirst("X-File-Last-Modified") =~ /\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}/

        and: "the markdown file is updated on disk"
        Files.readString(mdFile).contains("ORDER BY id")
        !Files.readString(mdFile).contains("query: SELECT id, label FROM items\n")

        and: "the old cache file is deleted"
        !Files.exists(cacheFile)

        cleanup:
        conn?.close()
    }

    def "edit - re-derives shared params from parameter blocks preceding the data block"() {
        given: "a parameter block followed by a data block that depends on the shared param"
        def dbName = "editParamDb_${System.nanoTime()}"
        def url = "jdbc:h2:mem:${dbName};DB_CLOSE_DELAY=-1"
        def conn = java.sql.DriverManager.getConnection(url, "sa", "")
        conn.createStatement().execute("CREATE TABLE nums (val INT)")
        conn.createStatement().execute("INSERT INTO nums VALUES (7)")
        conn.createStatement().execute("INSERT INTO nums VALUES (99)")

        def configService = Mock(ConfigService)
        configService.getDocsDirectory() >> tempDir.toString()
        configService.getSqlMaxRows() >> 100
        configService.getDataSourceConfig("paramDs") >> new ConfigService.DataSourceConfig("paramDs", url, "sa", "")
        def exchangeRateService = Mock(ExchangeRateService)
        exchangeRateService.getVersion() >> 0L
        def controller = new DataBlockRefreshController(configService, exchangeRateService)

        def dataLiteral = "source: paramDs\nquery: SELECT val FROM nums WHERE val = :myParam\nparameters:\n  myParam:\n    value: 0\n    type: integer\n"
        // The server re-derives: paramBlock sets myParam=7 in registry → checksum includes myParam=7
        def mapper = new com.fasterxml.jackson.databind.ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory())
        def config = mapper.readValue(dataLiteral, uk.anbu.devnotes.markdown.code.datablock.YamlCodeblockConfig)
        def blockId = config.checksum([myParam: 7, __exchangeRatesVersion: 0L])

        def mdContent = "```parameter\nmyParam: 7\n```\n\n```data\n${dataLiteral}```\n"
        tempDir.resolve("parampage.md").toFile().text = mdContent

        when: "edit is called with the blockId that includes the shared param in its checksum"
        def response = controller.editBlock([
                markdownFile: "parampage.md",
                blockId     : blockId,
                newContent  : dataLiteral   // same content, just triggering a re-render
        ])

        then: "block is found (proving shared params were re-derived from the parameter block)"
        response.statusCode == HttpStatus.OK
        response.body.contains("<table")

        cleanup:
        conn?.close()
    }

    def "edit - still finds block when exchange-rate version changed since page render"() {
        given:
        def dbName = "editFxVersionDb_${System.nanoTime()}"
        def url = "jdbc:h2:mem:${dbName};DB_CLOSE_DELAY=-1"
        def conn = java.sql.DriverManager.getConnection(url, "sa", "")
        conn.createStatement().execute("CREATE TABLE users (id INT, first_name VARCHAR(100))")
        conn.createStatement().execute("INSERT INTO users VALUES (1, 'Alice')")

        def configService = Mock(ConfigService)
        configService.getDocsDirectory() >> tempDir.toString()
        configService.getSqlMaxRows() >> 100
        configService.getDataSourceConfig("datasource1") >> new ConfigService.DataSourceConfig("datasource1", url, "sa", "")

        def exchangeRateService = Mock(ExchangeRateService)
        exchangeRateService.getVersion() >> 123456789L

        def controller = new DataBlockRefreshController(configService, exchangeRateService)

        def literal = "source: datasource1\nquery: |\n  SELECT id,first_name FROM users\nheader: 'Users'\n"
        // Simulate a block id computed without the exchange-rate version key.
        def oldVersionBlockId = computeChecksum(literal)

        def mdFile = tempDir.resolve("users.md")
        mdFile.toFile().text = "```data\n${literal}```\n"

        when:
        def response = controller.editBlock([
                markdownFile: "users.md",
                blockId     : oldVersionBlockId,
                newContent  : literal
        ])

        then:
        response.statusCode == HttpStatus.OK
        response.body.contains("<table")
        response.body.contains("Users")

        cleanup:
        conn?.close()
    }

    def "edit - finds nested data blocks inside blockquotes"() {
        given:
        def dbName = "editNestedDb_${System.nanoTime()}"
        def url = "jdbc:h2:mem:${dbName};DB_CLOSE_DELAY=-1"
        def conn = java.sql.DriverManager.getConnection(url, "sa", "")
        conn.createStatement().execute("CREATE TABLE users (id INT, first_name VARCHAR(100))")
        conn.createStatement().execute("INSERT INTO users VALUES (1, 'Nested')")

        def configService = Mock(ConfigService)
        configService.getDocsDirectory() >> tempDir.toString()
        configService.getSqlMaxRows() >> 100
        configService.getDataSourceConfig("datasource1") >> new ConfigService.DataSourceConfig("datasource1", url, "sa", "")

        def exchangeRateService = Mock(ExchangeRateService)
        exchangeRateService.getVersion() >> 0L

        def controller = new DataBlockRefreshController(configService, exchangeRateService)

        def literal = "source: datasource1\nquery: SELECT id, first_name FROM users\n"
        def blockId = computeEditChecksum(literal, 0L)

        tempDir.resolve("nested.md").toFile().text = "> ```data\n> ${literal.replace("\n", "\n> ")}\n> ```\n"

        when:
        def response = controller.editBlock([
                markdownFile: "nested.md",
                blockId     : blockId,
                newContent  : literal
        ])

        then:
        response.statusCode == HttpStatus.OK
        response.body.contains("Nested")

        cleanup:
        conn?.close()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String computeChecksum(String yaml) {
        def mapper = new com.fasterxml.jackson.databind.ObjectMapper(
                new com.fasterxml.jackson.dataformat.yaml.YAMLFactory())
        def config = mapper.readValue(yaml, uk.anbu.devnotes.markdown.code.datablock.YamlCodeblockConfig)
        return config.checksum([:])
    }

    /** Compute checksum the same way the edit endpoint does (shared params include __exchangeRatesVersion). */
    private static String computeEditChecksum(String yaml, long exchangeRatesVersion = 0L) {
        def mapper = new com.fasterxml.jackson.databind.ObjectMapper(
                new com.fasterxml.jackson.dataformat.yaml.YAMLFactory())
        def config = mapper.readValue(yaml, uk.anbu.devnotes.markdown.code.datablock.YamlCodeblockConfig)
        return config.checksum([__exchangeRatesVersion: exchangeRatesVersion])
    }
}