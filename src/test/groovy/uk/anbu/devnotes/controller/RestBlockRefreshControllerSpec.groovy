package uk.anbu.devnotes.controller

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir
import uk.anbu.devnotes.service.ConfigService

import java.nio.file.Files
import java.nio.file.Path

import static com.github.tomakehurst.wiremock.client.WireMock.*

class RestBlockRefreshControllerSpec extends Specification {

    @Shared
    WireMockServer wireMock

    @TempDir
    Path tempDir

    RestBlockRefreshController controller

    def setupSpec() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())
        wireMock.start()
    }

    def cleanupSpec() {
        wireMock.stop()
    }

    def setup() {
        wireMock.resetAll()
        def configService = Mock(ConfigService)
        configService.getDocsDirectory() >> tempDir.toString()
        controller = new RestBlockRefreshController(configService)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private int port() { wireMock.port() }

    private String writeMarkdown(String filename, String yaml) {
        def content = "```rest\n${yaml}\n```\n"
        Files.writeString(tempDir.resolve(filename), content)
        return content
    }

    private String computeChecksum(String yaml) {
        def mapper = new com.fasterxml.jackson.databind.ObjectMapper(
                new com.fasterxml.jackson.dataformat.yaml.YAMLFactory())
        def config = mapper.readValue(yaml,
                uk.anbu.devnotes.markdown.code.restblock.RestCodeblockConfig)
        return config.checksum()
    }

    // -------------------------------------------------------------------------
    // 1. Happy path
    // -------------------------------------------------------------------------

    def "refresh endpoint returns rendered HTML for a matching restblock-id"() {
        given:
        wireMock.stubFor(get(urlEqualTo("/refresh-ok"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody('[{"id":1,"name":"Alice"}]')))

        def yaml = "url: http://localhost:${port()}/refresh-ok\n"
        writeMarkdown("test.md", yaml)
        def checksum = computeChecksum(yaml)

        when:
        def response = controller.renderFragment([
                markdownFile: "test.md",
                restblockId : checksum
        ])

        then:
        response.statusCode == HttpStatus.OK
        response.headers.getContentType().isCompatibleWith(MediaType.TEXT_HTML)
        response.body.contains("rest-block")
        response.body.contains("Alice")
    }

    // -------------------------------------------------------------------------
    // 2. restblock-id not found → 404
    // -------------------------------------------------------------------------

    def "refresh endpoint returns 404 when restblock-id is not found in the markdown file"() {
        given:
        def yaml = "url: http://localhost:${port()}/some-path\n"
        writeMarkdown("test-404.md", yaml)

        when:
        def response = controller.renderFragment([
                markdownFile: "test-404.md",
                restblockId : "aaaaaaaaaaaaaaaa"
        ])

        then:
        response.statusCode == HttpStatus.NOT_FOUND
    }

    // -------------------------------------------------------------------------
    // 3. Missing markdownFile field → 400
    // -------------------------------------------------------------------------

    def "refresh endpoint returns 400 when markdownFile field is missing"() {
        when:
        def response = controller.renderFragment([restblockId: "abc"])

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
    }

    // -------------------------------------------------------------------------
    // 4. markdownFile does not exist on disk → 400
    // -------------------------------------------------------------------------

    def "refresh endpoint returns 400 when markdownFile does not exist on disk"() {
        when:
        def response = controller.renderFragment([
                markdownFile: "nonexistent.md",
                restblockId : "abc"
        ])

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
    }

    // -------------------------------------------------------------------------
    // 5. Missing restblockId field → 400
    // -------------------------------------------------------------------------

    def "refresh endpoint returns 400 when restblockId field is missing"() {
        when:
        def response = controller.renderFragment([markdownFile: "test.md"])

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
    }

    // -------------------------------------------------------------------------
    // 6. Cache file is deleted and a fresh one is written
    // -------------------------------------------------------------------------

    def "refresh deletes the existing cache file and writes a fresh one"() {
        given:
        wireMock.stubFor(get(urlEqualTo("/refresh-cache"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody('[{"id":2}]')))

        def yaml = "url: http://localhost:${port()}/refresh-cache\n"
        writeMarkdown("cache-refresh.md", yaml)
        def checksum = computeChecksum(yaml)

        // Write a fake stale cache file
        def translatorInstance = new uk.anbu.devnotes.markdown.code.RestBlockTranslator()
        def fakeMdFile = new uk.anbu.devnotes.types.MarkdownFile(tempDir, "cache-refresh.md")
        def cacheFile = translatorInstance.cacheFilePath(fakeMdFile, checksum)
        Files.writeString(cacheFile, "<div>stale</div>")

        when:
        def response = controller.renderFragment([
                markdownFile: "cache-refresh.md",
                restblockId : checksum
        ])

        then:
        response.statusCode == HttpStatus.OK
        // The stale content should have been replaced with fresh data
        response.body.contains("rest-block")
        !response.body.contains("stale")
        // A new cache file should exist
        Files.exists(cacheFile)
        !Files.readString(cacheFile).contains("stale")
    }
}