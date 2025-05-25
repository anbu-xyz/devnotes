package uk.anbu.devnotes.service

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

class PomodoroServiceTest extends Specification {

    @TempDir
    Path tempDir

    PomodoroService pomodoroService
    ConfigService configService

    def setup() {
        configService = Mock(ConfigService)
        pomodoroService = new PomodoroService(configService)
    }

    def "loadPomodoroConfigFrom should create new config file when it doesn't exist"() {
        given:
        def docsDir = tempDir.toString()

        when:
        def result = pomodoroService.loadPomodoroConfigFrom(docsDir)

        then:
        def configFile = tempDir.resolve("config/pomodoro.yaml")
        Files.exists(configFile)
        result.state() == PomodoroService.PomodoroState.NOT_STARTED
        result.timeLeftInSeconds() == PomodoroService.DEFAULT_MINUTES * 60
        result.overallDurationInSeconds() == PomodoroService.DEFAULT_MINUTES * 60
        result.startedAtUtc() != null
    }

    def "loadPomodoroConfigFrom should read existing config file"() {
        given:
        def docsDir = tempDir.toString()
        def configDir = Files.createDirectories(tempDir.resolve("config"))
        def configFile = configDir.resolve("pomodoro.yaml")
        Files.writeString(configFile, """
            startedAtUtc: "2023-01-01T10:00"
            timeLeftInSeconds: 900
            overallDurationInSeconds: 1500
            state: "PAUSED"
        """)

        when:
        def result = pomodoroService.loadPomodoroConfigFrom(docsDir)

        then:
        result.state() == PomodoroService.PomodoroState.PAUSED
        result.timeLeftInSeconds() == 900
        result.overallDurationInSeconds() == 1500
        result.startedAtUtc() == LocalDateTime.parse("2023-01-01T10:00")
    }

    def "savePomodoroConfig should write config to file"() {
        given:
        def startTime = LocalDateTime.parse("2023-01-01T10:00")
        def config = new PomodoroService.PomodoroConfig(
                startTime,
                900,
                1500,
                PomodoroService.PomodoroState.RUNNING
        )
        configService.getDocsDirectory() >> tempDir.toString()

        when:
        pomodoroService.savePomodoroConfig(config)

        then:
        def configFile = tempDir.resolve("config/pomodoro.yaml")
        def content = Files.readString(configFile)
        content.contains("startedAtUtc: \"2023-01-01T10:00\"")
        content.contains("timeLeft: 900")
        content.contains("overallDuration: 1500")
        content.contains("state: \"RUNNING\"")
    }

    def "loadPomodoroConfigFrom should create gitignore file"() {
        given:
        def docsDir = tempDir.toString()

        when:
        pomodoroService.loadPomodoroConfigFrom(docsDir)

        then:
        def gitFile = tempDir.resolve("config/.git")
        Files.exists(gitFile)
        Files.readString(gitFile).contains("pomodoro.yaml")
    }

    def "should correctly read back saved pomodoro config"() {
        given:
        configService.getDocsDirectory() >> tempDir.toString()
        def startTime = LocalDateTime.parse("2023-01-01T10:00:00")
        def originalConfig = new PomodoroService.PomodoroConfig(
                startTime,
                900,
                1500,
                PomodoroService.PomodoroState.RUNNING
        )

        when: "saving the config"
        pomodoroService.savePomodoroConfig(originalConfig)

        and: "reading it back"
        def readConfig = pomodoroService.loadPomodoroConfigFrom(tempDir.toString())

        then: "all values should match the original"
        readConfig.startedAtUtc() == originalConfig.startedAtUtc()
        readConfig.timeLeftInSeconds() == originalConfig.timeLeftInSeconds()
        readConfig.overallDurationInSeconds() == originalConfig.overallDurationInSeconds()
        readConfig.state() == originalConfig.state()

        and: "the config file should exist"
        def configFile = tempDir.resolve("config/pomodoro.yaml")
        Files.exists(configFile)
    }
}
