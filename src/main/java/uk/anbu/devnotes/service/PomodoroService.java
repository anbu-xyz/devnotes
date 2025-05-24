package uk.anbu.devnotes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
        Path docsDirectory = Path.of(getDocsDirectory());
        Path configDirectory = docsDirectory.resolve("config");
        Path pomodoroPath = configDirectory.resolve(POMODORO_FILENAME);
        Path dotGitPath = configDirectory.resolve(GIT_FILENAME);

        Files.createDirectories(configDirectory);
        createGitFile(dotGitPath);

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        Map<String, Object> config = new HashMap<>();

        if (!Files.exists(pomodoroPath)) {
            config.put("minutesLeft", DEFAULT_MINUTES);
            mapper.writeValue(pomodoroPath.toFile(), config);
        } else {
            config = mapper.readValue(pomodoroPath.toFile(), Map.class);
        }

        return new PomodoroConfig(config.containsKey("minutesLeft") ?
                (Integer) config.get("minutesLeft") : DEFAULT_MINUTES);
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
        Path configPath = Path.of(getDocsDirectory(), "config", POMODORO_FILENAME);
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("minutesLeft", config.minutesLeft());
        mapper.writeValue(configPath.toFile(), configMap);
    }

    public record PomodoroConfig(Integer minutesLeft) {
    }
}