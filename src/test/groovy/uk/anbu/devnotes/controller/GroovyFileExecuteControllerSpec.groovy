package uk.anbu.devnotes.controller

import org.springframework.http.HttpStatus
import spock.lang.Specification
import spock.lang.TempDir
import uk.anbu.devnotes.service.ConfigService

import java.nio.file.Files
import java.nio.file.Path

class GroovyFileExecuteControllerSpec extends Specification {

    @TempDir
    Path tempDir

    GroovyFileExecuteController controller

    void setup() {
        ConfigService configService = Mock(ConfigService)
        configService.getDocsDirectory() >> tempDir.toString()
        controller = new GroovyFileExecuteController(configService)
    }

    void "POST /groovy/execute-file captures println output in stdout"() {
        given:
        Files.writeString(tempDir.resolve("hello.groovy"), 'println "hello world"')

        when:
        def response = controller.executeFile(".", "hello.groovy")

        then:
        response.statusCode == HttpStatus.OK
        response.body.stdout().contains("hello world")
        response.body.stderr().isEmpty()
    }

    void "POST /groovy/execute-file captures exception stack trace in stderr"() {
        given:
        Files.writeString(tempDir.resolve("error.groovy"), 'throw new RuntimeException("boom")')

        when:
        def response = controller.executeFile(".", "error.groovy")

        then:
        response.statusCode == HttpStatus.OK
        response.body.stderr().contains("boom")
    }

    void "POST /groovy/execute-file captures both stdout and stderr"() {
        given:
        Files.writeString(tempDir.resolve("both.groovy"), '''\
println "out line"
System.err.println "err line"
''')

        when:
        def response = controller.executeFile(".", "both.groovy")

        then:
        response.statusCode == HttpStatus.OK
        response.body.stdout().contains("out line")
        response.body.stderr().contains("err line")
    }

    void "POST /groovy/execute-file returns 404 when script file does not exist"() {
        when:
        def response = controller.executeFile(".", "missing.groovy")

        then:
        response.statusCode == HttpStatus.NOT_FOUND
        response.body.stderr().contains("missing.groovy")
    }

    void "POST /groovy/execute-file path parameter with traversal is neutralised and returns 404"() {
        // FileUtil.cleanDirectoryName strips leading ../ segments so the resolved path
        // stays inside docsRoot; since "etc/passwd" does not exist there, we get 404
        when:
        def response = controller.executeFile("../../etc", "passwd")

        then:
        response.statusCode == HttpStatus.NOT_FOUND
    }

    void "POST /groovy/execute-file rejects path traversal in name parameter"() {
        given:
        def subDir = Files.createDirectory(tempDir.resolve("sub"))
        Files.writeString(subDir.resolve("script.groovy"), 'println "safe"')

        when:
        def response = controller.executeFile("sub", "../../outside.groovy")

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
        response.body.stderr().contains("access denied")
    }

    void "POST /groovy/execute-file works in a subdirectory"() {
        given:
        def subDir = Files.createDirectory(tempDir.resolve("scripts"))
        Files.writeString(subDir.resolve("calc.groovy"), 'println 2 + 2')

        when:
        def response = controller.executeFile("scripts", "calc.groovy")

        then:
        response.statusCode == HttpStatus.OK
        response.body.stdout().contains("4")
    }

    void "POST /groovy/execute-file returns empty stdout for script with no output"() {
        given:
        Files.writeString(tempDir.resolve("silent.groovy"), 'def x = 1 + 1')

        when:
        def response = controller.executeFile(".", "silent.groovy")

        then:
        response.statusCode == HttpStatus.OK
        response.body.stdout().isEmpty()
        response.body.stderr().isEmpty()
    }
}