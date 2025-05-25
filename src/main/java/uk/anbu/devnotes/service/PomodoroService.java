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
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PomodoroService {

    public static final int DEFAULT_MINUTES = 25;
    private static final String POMODORO_FILENAME = "pomodoro.yaml";
    private static final String GIT_FILENAME = ".git";
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
            config.put("overallDuration", Duration.ofMinutes(DEFAULT_MINUTES));
            config.put("startedAtUtc", LocalDateTime.now());
            config.put("timeLeft", Duration.ofMinutes(DEFAULT_MINUTES));
            config.put("state", PomodoroState.NOT_STARTED);
            mapper.writeValue(pomodoroPath.toFile(), config);
            return new PomodoroConfig(
                    LocalDateTime.now(),
                    DEFAULT_MINUTES * 60,
                    DEFAULT_MINUTES * 60,
                    PomodoroState.NOT_STARTED
            );
        } else {
            config = mapper.readValue(pomodoroPath.toFile(), Map.class);
            PomodoroConfig pomodoroConfig = new PomodoroConfig(
                    LocalDateTime.parse(config.get("startedAtUtc").toString()),
                    Long.parseLong(config.get("timeLeftInSeconds").toString()),
                    Long.parseLong(config.get("overallDurationInSeconds").toString()),
                    PomodoroState.valueOf(config.get("state").toString())
            );
            return pomodoroConfig;
        }
    }

    private static Path getPomodoroFilePath(String docsDirectory) throws IOException {
        Path docsDirectoryPath = Path.of(docsDirectory);
        Path configDirectory = docsDirectoryPath.resolve("config");
        Path pomodoroPath = configDirectory.resolve(POMODORO_FILENAME);
        Path dotGitPath = configDirectory.resolve(GIT_FILENAME);

        Files.createDirectories(configDirectory);
        createGitFile(dotGitPath);
        return pomodoroPath;
    }

    private static void createGitFile(Path dotGitPath) throws IOException {
        if (Files.exists(dotGitPath)) {
            String content = Files.readString(dotGitPath, StandardCharsets.UTF_8);
            if (!content.contains(POMODORO_FILENAME)) {
                Files.writeString(dotGitPath, content + "\n" + POMODORO_FILENAME, StandardCharsets.UTF_8);
            }
        } else {
            Files.writeString(dotGitPath, POMODORO_FILENAME, StandardCharsets.UTF_8);
        }
    }

    public void savePomodoroConfig(PomodoroConfig config) throws IOException {
        Path configPath = getPomodoroFilePath(getDocsDirectory());

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("startedAtUtc", config.startedAtUtc().toString());
        configMap.put("timeLeftInSeconds", config.timeLeftInSeconds());
        configMap.put("overallDurationInSeconds", config.overallDurationInSeconds());
        configMap.put("state", config.state());
        mapper.writeValue(configPath.toFile(), configMap);
    }

    public record PomodoroConfig(LocalDateTime startedAtUtc,
                                 long timeLeftInSeconds,
                                 long overallDurationInSeconds,
                                 PomodoroState state) {
    }

    public enum PomodoroState {
        NOT_STARTED,
        RUNNING,
        PAUSED,
        STOPPED
    }
}