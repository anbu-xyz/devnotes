package uk.anbu.devnotes.service

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

import static java.time.ZoneOffset.UTC;

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
        content.contains("timeLeftInSeconds: 900")
        content.contains("overallDurationInSeconds: 1500")
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

    def "should calculate correct time left when loading a running timer"() {
        given: "a config file with a running timer"
        def docsDir = tempDir.toString()
        def configDir = Files.createDirectories(tempDir.resolve("config"))
        def configFile = configDir.resolve("pomodoro.yaml")
        def startTime = LocalDateTime.now(UTC).minusMinutes(2) // started 2 minutes ago
        def originalDuration = 5 * 60 // 5 minutes total duration

        Files.writeString(configFile, """
        startedAtUtc: "${startTime}"
        timeLeftInSeconds: ${originalDuration}
        overallDurationInSeconds: ${originalDuration}
        state: "RUNNING"
    """)

        when: "loading the config"
        def result = pomodoroService.loadPomodoroConfigFrom(docsDir)

        then: "time left should be approximately 3 minutes"
        result.state() == PomodoroService.PomodoroState.RUNNING
        result.overallDurationInSeconds() == originalDuration
        result.startedAtUtc() == startTime
        result.timeLeftInSeconds() <= (3 * 60) // should have ~3 minutes left
        result.timeLeftInSeconds() > (2 * 60) // but more than 2 minutes
    }

    def "should set time left to zero when loading an expired running timer"() {
        given: "a config file with an expired running timer"
        def docsDir = tempDir.toString()
        def configDir = Files.createDirectories(tempDir.resolve("config"))
        def configFile = configDir.resolve("pomodoro.yaml")
        def startTime = LocalDateTime.now(UTC).minusMinutes(10) // started 10 minutes ago
        def originalDuration = 5 * 60 // 5 minutes total duration

        Files.writeString(configFile, """
        startedAtUtc: "${startTime}"
        timeLeftInSeconds: ${originalDuration}
        overallDurationInSeconds: ${originalDuration}
        state: "RUNNING"
    """)

        when: "loading the config"
        def result = pomodoroService.loadPomodoroConfigFrom(docsDir)

        then: "time left should be zero"
        result.state() == PomodoroService.PomodoroState.RUNNING
        result.overallDurationInSeconds() == originalDuration
        result.startedAtUtc() == startTime
        result.timeLeftInSeconds() == 0
    }
}
