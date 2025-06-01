package uk.anbu.devnotes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static java.time.ZoneOffset.UTC;

@Service
@RequiredArgsConstructor
@Slf4j
public class PomodoroService {

    public static final int DEFAULT_MINUTES = 25;
    private static final String POMODORO_FILENAME = "pomodoro.yaml";
    private static final String GIT_IGNORE_FILENAME = ".gitignore";
    private final ConfigService configService;

    public String getDocsDirectory() {
        return configService.getDocsDirectory();
    }

    public PomodoroConfig loadPomodoroConfig() throws IOException {
        return loadPomodoroConfigFrom(getDocsDirectory());
    }

    public PomodoroConfig loadPomodoroConfigFrom(String docsDirectory) throws IOException {
        Path pomodoroPath = getPomodoroFilePath(docsDirectory);

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());
        Map<String, Object> config = new HashMap<>();

        if (!Files.exists(pomodoroPath)) {
            config.put("updateTimestamp", LocalDateTime.now(UTC).truncatedTo(ChronoUnit.SECONDS).toString());
            config.put("timeLeftInSeconds", DEFAULT_MINUTES * 60);
            config.put("state", PomodoroState.NOT_STARTED);
            mapper.writeValue(pomodoroPath.toFile(), config);
            return new PomodoroConfig(
                    LocalDateTime.now(UTC).truncatedTo(ChronoUnit.SECONDS),
                    DEFAULT_MINUTES * 60,
                    PomodoroState.NOT_STARTED
            );
        } else {
            config = mapper.readValue(pomodoroPath.toFile(), Map.class);
            PomodoroConfig pomodoroConfig = new PomodoroConfig(
                    LocalDateTime.parse(config.get("updateTimestamp").toString()),
                    Long.parseLong(config.get("timeLeftInSeconds").toString()),
                    PomodoroState.valueOf(config.get("state").toString())
            );
            if (pomodoroConfig.state() == PomodoroState.RUNNING) {
                var timeLeft = pomodoroConfig.timeLeftInSeconds()
                        - Duration.between(pomodoroConfig.updateTimestamp(), LocalDateTime.now(UTC)).getSeconds();
                if (timeLeft < 0) {
                    timeLeft = 0;
                }
                pomodoroConfig = new PomodoroConfig(
                        pomodoroConfig.updateTimestamp(),
                        timeLeft,
                        pomodoroConfig.state()
                );
            }
            return pomodoroConfig;
        }
    }

    private static Path getPomodoroFilePath(String docsDirectory) throws IOException {
        Path docsDirectoryPath = Path.of(docsDirectory);
        Path configDirectory = docsDirectoryPath.resolve("config");
        Path pomodoroPath = configDirectory.resolve(POMODORO_FILENAME);
        Path dotGitPath = configDirectory.resolve(GIT_IGNORE_FILENAME);

        Files.createDirectories(configDirectory);
        createGitIgnoreFile(dotGitPath);
        return pomodoroPath;
    }

    private static void createGitIgnoreFile(Path dotGitPath) throws IOException {
        if (Files.exists(dotGitPath)) {
            String content = Files.readString(dotGitPath, StandardCharsets.UTF_8);
            if (!content.contains(POMODORO_FILENAME)) {
                Files.writeString(dotGitPath, content + "\n" + POMODORO_FILENAME, StandardCharsets.UTF_8);
            }
        } else {
            Files.writeString(dotGitPath, POMODORO_FILENAME + "\n", StandardCharsets.UTF_8);
        }
    }

    public void savePomodoroConfig(PomodoroConfig config) throws IOException {
        Path configPath = getPomodoroFilePath(getDocsDirectory());

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("updateTimestamp", config.updateTimestamp.truncatedTo(ChronoUnit.SECONDS).toString());
        configMap.put("timeLeftInSeconds", config.timeLeftInSeconds());
        configMap.put("state", config.state());
        mapper.writeValue(configPath.toFile(), configMap);
    }

    public PomodoroConfig startPomodoro(long timeLeftInSeconds) throws IOException {
        PomodoroConfig config = new PomodoroConfig(
                LocalDateTime.now(UTC),
                timeLeftInSeconds,
                PomodoroState.RUNNING
        );
        savePomodoroConfig(config);
        return config;
    }

    public record PomodoroConfig(LocalDateTime updateTimestamp,
                                 long timeLeftInSeconds,
                                 PomodoroState state) {
    }

    public enum PomodoroState {
        NOT_STARTED,
        RUNNING,
        PAUSED
    }
}