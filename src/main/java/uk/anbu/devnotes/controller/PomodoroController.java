package uk.anbu.devnotes.controller;

import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devnotes.service.PomodoroService;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static java.time.ZoneOffset.UTC;

@RestController
@RequiredArgsConstructor
@Slf4j
public class PomodoroController {

    private final TemplateEngine templateEngine;
    private final PomodoroService pomodoroService;

    @GetMapping("/pomodoro")
    public ResponseEntity<String> pomodoroPage() {
        PomodoroService.PomodoroConfig pomodoroConfig = null;
        try {
            pomodoroConfig = pomodoroService.loadPomodoroConfig();
        } catch (IOException e) {
            log.error("Failed to load pomodoro config", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to process pomodoro page: " + e.getMessage());
        }

        var model = new HashMap<String, Object>();
        model.put("pomodoroConfig", pomodoroConfig);
        TemplateOutput output = new StringOutput();
        templateEngine.render("pomodoro.jte", model, output);
        return ResponseEntity.ok(output.toString());
    }

    @PostMapping("/pomodoro")
    public ResponseEntity<String> pomodoro(@RequestBody PomodoroService.PomodoroConfig pomodoroConfig) {
        try {
            if (pomodoroConfig.updateTimestamp() == null) {
                pomodoroConfig = new PomodoroService.PomodoroConfig(
                        LocalDateTime.now(UTC),
                        pomodoroConfig.timeLeftInSeconds(),
                        pomodoroConfig.state()
                );
            }
            pomodoroService.savePomodoroConfig(pomodoroConfig);
            return ResponseEntity.ok("Timer updated successfully");
        } catch (IOException e) {
            log.error("Failed to update timer", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to update timer: " + e.getMessage());
        }
    }

    @PostMapping("/pomodoro/start")
    public ResponseEntity<Map<String, String>> startPomodoro(@RequestParam("timeLeftInSeconds") long timeLeftInSeconds) {
        try {
            PomodoroService.PomodoroConfig config = pomodoroService.startPomodoro(timeLeftInSeconds);
            Map<String, String> response = new HashMap<>();
            response.put("updateTimestamp", config.updateTimestamp().toString());
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("Failed to start timer", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to start timer: " + e.getMessage()));
        }
    }
}