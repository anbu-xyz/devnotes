package uk.anbu.devnotes.controller

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import spock.lang.Specification
import spock.lang.TempDir
import uk.anbu.devnotes.service.ConfigService

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
                "myDs", "jdbc:h2:mem:", "sa", "ENC(abc123==)", "org.h2.Driver")

        // Write a minimal markdown file with a data block
        def mdFile = tempDir.resolve("test.md")
        mdFile.toFile().text = '''\
```data
source: myDs
query: SELECT 1
```
'''

        def controller = new DataBlockRefreshController(configService)

        def body = [
                markdownFile: "test.md",
                datablockId : computeChecksum("source: myDs\nquery: SELECT 1\n"),
                params      : [:]
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

    def "returns 400 when markdownFile is missing"() {
        given:
        def configService = Mock(ConfigService)
        def controller = new DataBlockRefreshController(configService)

        when:
        def response = controller.renderFragment([datablockId: "abc"])

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
    }

    def "returns 400 when datablockId is missing"() {
        given:
        def configService = Mock(ConfigService)
        def controller = new DataBlockRefreshController(configService)

        when:
        def response = controller.renderFragment([markdownFile: "test.md"])

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
    }

    // -------------------------------------------------------------------------
    // Helper: compute checksum the same way DataBlockTranslator/YamlCodeblockConfig does
    // -------------------------------------------------------------------------
    private static String computeChecksum(String yaml) {
        // Parse the YAML via the same mapper used in the controller
        def mapper = new com.fasterxml.jackson.databind.ObjectMapper(
                new com.fasterxml.jackson.dataformat.yaml.YAMLFactory())
        def config = mapper.readValue(yaml, uk.anbu.devnotes.markdown.code.datablock.YamlCodeblockConfig)
        return config.checksum([:])
    }
}

