package uk.anbu.devnotes.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devnotes.service.PomodoroService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PomodoroController {

    private final TemplateEngine templateEngine;
    private final PomodoroService pomodoroService;
    private static final int DEFAULT_MINUTES = 25;
    private static final String POMODORO_FILENAME = "pomodoro.yaml";
    private static final String GIT_FILENAME = ".git";

    @GetMapping("/pomodoro")
    public ResponseEntity<String> pomodoroPage() {
        try {
            var model = new HashMap<String, Object>();
            Path docsDirectory = Path.of(pomodoroService.getDocsDirectory());
            Path configDirectory = docsDirectory.resolve("config");
            Path pomodoroPath = configDirectory.resolve(POMODORO_FILENAME);
            Path dotGitPath = configDirectory.resolve(GIT_FILENAME);

            // Create config directory if it doesn't exist
            Files.createDirectories(configDirectory);

            // Handle .git file
            if (Files.exists(dotGitPath)) {
                String content = Files.readString(dotGitPath, StandardCharsets.UTF_8);
                if (!content.contains(POMODORO_FILENAME)) {
                    Files.writeString(dotGitPath, content + "\n" + POMODORO_FILENAME, StandardCharsets.UTF_8);
                }
            } else {
                Files.writeString(dotGitPath, POMODORO_FILENAME, StandardCharsets.UTF_8);
            }

            // Handle pomodoro.yaml
            Map<String, Object> config;
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

            if (!Files.exists(pomodoroPath)) {
                config = new HashMap<>();
                config.put("minutesLeft", DEFAULT_MINUTES);
                mapper.writeValue(pomodoroPath.toFile(), config);
            } else {
                config = mapper.readValue(pomodoroPath.toFile(), Map.class);
            }

            int minutesLeft = config.containsKey("minutesLeft") ?
                    (Integer) config.get("minutesLeft") : DEFAULT_MINUTES;
            model.put("minutesLeft", minutesLeft);

            TemplateOutput output = new StringOutput();
            templateEngine.render("pomodoro.jte", model, output);
            return ResponseEntity.ok(output.toString());

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to process pomodoro page: " + e.getMessage());
        }
    }

    @PostMapping("/pomodoro")
    public ResponseEntity<String> pomodoro(@RequestParam("minutesLeft") Integer minutesLeft) {
        if (minutesLeft == null || minutesLeft < 0) {
            return ResponseEntity.badRequest().body("Invalid minutes value");
        }

        try {
            Path configPath = Path.of(pomodoroService.getDocsDirectory().toString(), "config", POMODORO_FILENAME);
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            Map<String, Object> config = new HashMap<>();
            config.put("minutesLeft", minutesLeft);
            mapper.writeValue(configPath.toFile(), config);
            return ResponseEntity.ok("Timer updated successfully");
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to update timer: " + e.getMessage());
        }
    }
}