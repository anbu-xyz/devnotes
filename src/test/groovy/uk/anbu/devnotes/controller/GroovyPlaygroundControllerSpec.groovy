package uk.anbu.devnotes.controller

import gg.jte.ContentType
import gg.jte.TemplateEngine
import gg.jte.TemplateOutput
import gg.jte.resolve.DirectoryCodeResolver
import org.jsoup.Jsoup
import org.springframework.http.HttpStatus
import spock.lang.Specification
import uk.anbu.devnotes.service.ConfigService

import java.nio.file.Files
import java.nio.file.Paths

class GroovyPlaygroundControllerSpec extends Specification {

    GroovyPlaygroundController controller
    TemplateEngine templateEngine
    ConfigService configService

    def setup() {
        def codeResolver = new DirectoryCodeResolver(Paths.get("src/main/jte"))
        templateEngine = TemplateEngine.create(codeResolver, Paths.get("src/main/jte"), ContentType.Html)
        configService = Mock(ConfigService)
        controller = new GroovyPlaygroundController(templateEngine, configService)
    }

    def "groovyPlayground() should render the playground template with default content when no saved file exists"() {
        given:
        def tempDir = Files.createTempDirectory("test")
        configService.getDocsDirectory() >> tempDir.toFile().absolutePath

        when:
        def response = controller.groovyPlayground()

        then:
        response.statusCode == HttpStatus.OK
        response.headers.getContentType().toString().contains("text/html")
        def doc = Jsoup.parse(response.body.toString())
        doc.select("title").text() == "Groovy Playground"
        doc.select(".container").size() == 1
        doc.select("#editor").val().contains("N, N squared")
        doc.select("#renderMode").size() == 1

        cleanup:
        tempDir.toFile().deleteDir()
    }

    def "groovyPlayground() should load previously saved script from disk"() {
        given:
        def tempDir = Files.createTempDirectory("test")
        def savedScript = '"Hello from saved script"'
        def autosavePath = tempDir.resolve("config/groovy-playground/last-edited.groovy")
        Files.createDirectories(autosavePath.parent)
        Files.writeString(autosavePath, savedScript)
        configService.getDocsDirectory() >> tempDir.toFile().absolutePath

        when:
        def response = controller.groovyPlayground()

        then:
        response.statusCode == HttpStatus.OK
        def doc = Jsoup.parse(response.body.toString())
        doc.select("#editor").val() == savedScript

        cleanup:
        tempDir.toFile().deleteDir()
    }

    def "groovyPlayground() should restore previously saved render mode"() {
        given:
        def tempDir = Files.createTempDirectory("test")
        def modePath = tempDir.resolve("config/groovy-playground/last-render-mode.txt")
        Files.createDirectories(modePath.parent)
        Files.writeString(modePath, "html")
        configService.getDocsDirectory() >> tempDir.toFile().absolutePath

        when:
        def response = controller.groovyPlayground()

        then:
        response.statusCode == HttpStatus.OK
        response.body.toString().contains("html")

        cleanup:
        tempDir.toFile().deleteDir()
    }

    def "autosave() should persist script and render mode to the config directory"() {
        given:
        def tempDir = Files.createTempDirectory("test")
        configService.getDocsDirectory() >> tempDir.toFile().absolutePath
        def body = [script: '"test script"', renderMode: "text"]

        when:
        def response = controller.autosave(body)

        then:
        response.statusCode == HttpStatus.OK
        Files.readString(tempDir.resolve("config/groovy-playground/last-edited.groovy")) == '"test script"'
        Files.readString(tempDir.resolve("config/groovy-playground/last-render-mode.txt")) == "text"

        cleanup:
        tempDir.toFile().deleteDir()
    }

    def "save() should write content to the specified relative path within the docs directory"() {
        given:
        def tempDir = Files.createTempDirectory("test")
        configService.getDocsDirectory() >> tempDir.toFile().absolutePath
        def content = '"println \\"hello\\""'
        def savePath = "scripts/hello.groovy"

        when:
        def response = controller.save(savePath, content)

        then:
        response.statusCode == HttpStatus.OK
        Files.readString(tempDir.resolve(savePath)) == content

        cleanup:
        tempDir.toFile().deleteDir()
    }

    def "save() should reject path traversal attempts"() {
        given:
        def tempDir = Files.createTempDirectory("test")
        configService.getDocsDirectory() >> tempDir.toFile().absolutePath

        when:
        def response = controller.save("../../etc/passwd", "malicious")

        then:
        response.statusCode == HttpStatus.BAD_REQUEST

        cleanup:
        tempDir.toFile().deleteDir()
    }

    def "execute() should run a groovy script and return text output"() {
        given:
        configService.getChromeDriverLocation() >> Optional.empty()
        def body = [script: '"Hello, Playground!"', renderMode: "text"]

        when:
        def response = controller.execute(body)

        then:
        response.statusCode == HttpStatus.OK
        response.body.contains("Hello, Playground!")
    }

    def "execute() should run a groovy script and return html output"() {
        given:
        configService.getChromeDriverLocation() >> Optional.empty()
        def body = [script: '"<b>bold</b>"', renderMode: "html"]

        when:
        def response = controller.execute(body)

        then:
        response.statusCode == HttpStatus.OK
        response.body.contains("<b>bold</b>")
    }

    def "execute() should return a csv-table-with-header as an html table"() {
        given:
        configService.getChromeDriverLocation() >> Optional.empty()
        def script = '''"Name,Score\\nAlice,95\\nBob,87"'''
        def body = [script: script, renderMode: "csv-table-with-header"]

        when:
        def response = controller.execute(body)

        then:
        response.statusCode == HttpStatus.OK
        def doc = Jsoup.parse(response.body)
        doc.select("th").collect { it.text() } == ["Name", "Score"]
        doc.select("tr").size() == 3  // header + 2 data rows

        cleanup: "no files to clean up"
    }

    def "execute() should embed groovy errors in the response rather than returning a 500"() {
        given:
        configService.getChromeDriverLocation() >> Optional.empty()
        def body = [script: "throw new RuntimeException('test error')", renderMode: "text"]

        when:
        def response = controller.execute(body)

        then:
        response.statusCode == HttpStatus.OK
        response.body.contains("test error")
    }

    def "groovyPlayground() should surface template engine errors"() {
        given:
        def tempDir = Files.createTempDirectory("test")
        configService.getDocsDirectory() >> tempDir.toFile().absolutePath
        def failingEngine = Mock(TemplateEngine)
        failingEngine.render(_ as String, _ as Map, _ as TemplateOutput) >> {
            throw new IllegalStateException("boom")
        }
        def failingController = new GroovyPlaygroundController(failingEngine, configService)

        when:
        def response = failingController.groovyPlayground()

        then:
        response.statusCode == HttpStatus.INTERNAL_SERVER_ERROR
        response.body.toString().contains("Error rendering groovy playground: boom")

        cleanup:
        tempDir.toFile().deleteDir()
    }
}