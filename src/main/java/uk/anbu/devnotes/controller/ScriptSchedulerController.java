package uk.anbu.devnotes.controller;

import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devnotes.service.ScriptSchedulerService;
import uk.anbu.devnotes.types.ScriptSchedule;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/scheduler")
@Slf4j
@RequiredArgsConstructor
public class ScriptSchedulerController {
    private final TemplateEngine templateEngine;
    private final ScriptSchedulerService schedulerService;

    @GetMapping
    public ResponseEntity<String> showScheduler() {
        try {
            var model = new HashMap<String, Object>();
            model.put("schedules", schedulerService.loadSchedules());

            TemplateOutput output = new StringOutput();
            templateEngine.render("tools/script-scheduler.jte", model, output);
            return ResponseEntity.ok(output.toString());
        } catch (IOException e) {
            log.error("Failed to load schedules", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to load schedules: " + e.getMessage());
        }
    }

    @PostMapping("/add")
    public ResponseEntity<String> addSchedule(
            @RequestParam String cronExpression,
            @RequestParam String scriptFile) {
        try {
            String id = UUID.randomUUID().toString();
            ScriptSchedule schedule = new ScriptSchedule(id, cronExpression, scriptFile);

            List<ScriptSchedule> schedules = schedulerService.loadSchedules();
            schedules.add(schedule);
            schedulerService.saveSchedules(schedules);
            schedulerService.scheduleJob(schedule);

            return ResponseEntity.ok("Script scheduled successfully");
        } catch (Exception e) {
            log.error("Failed to schedule script", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to schedule script: " + e.getMessage());
        }
    }

    @PostMapping("/delete/{id}")
    public ResponseEntity<String> deleteSchedule(@PathVariable String id) {
        try {
            boolean deleted = schedulerService.deleteJob(id);
            if (!deleted) {
                return ResponseEntity.notFound().build();
            }
            log.info("Deleted script schedule with id {}", id);

            List<ScriptSchedule> schedules = schedulerService.loadSchedules();
            schedules.removeIf(s -> s.id().equals(id));
            schedulerService.saveSchedules(schedules);

            return ResponseEntity.ok("Script schedule deleted successfully");
        } catch (Exception e) {
            log.error("Failed to delete script schedule", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to delete script schedule: " + e.getMessage());
        }
    }
}