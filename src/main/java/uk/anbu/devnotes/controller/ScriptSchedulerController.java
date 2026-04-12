package uk.anbu.devnotes.controller;

import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devnotes.service.ScriptSchedulerService;
import uk.anbu.devnotes.types.LogEntry;
import uk.anbu.devnotes.types.ScriptSchedule;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            var schedules = schedulerService.loadSchedules();
            var lastRanMap = new HashMap<String, String>();
            var nextRunMap = new HashMap<String, String>();
            for (var schedule : schedules) {
                lastRanMap.put(schedule.id(),
                        schedulerService.getLastRanDisplay(schedule.scriptFile()));
                nextRunMap.put(schedule.id(),
                        schedulerService.getNextRunDisplay(schedule.cronExpression()));
            }
            var model = new HashMap<String, Object>();
            model.put("schedules", schedules);
            model.put("lastRanMap", lastRanMap);
            model.put("nextRunMap", nextRunMap);

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

            var model = new HashMap<String, Object>();
            model.put("schedule", schedule);
            model.put("lastRan", "Never");
            model.put("nextRun", schedulerService.getNextRunDisplay(schedule.cronExpression()));

            TemplateOutput output = new StringOutput();
            templateEngine.render("tools/schedule-item.jte", model, output);
            return ResponseEntity.ok(output.toString());
        } catch (Exception e) {
            log.error("Failed to schedule script", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to schedule script: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
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

            return ResponseEntity.ok("");
        } catch (Exception e) {
            log.error("Failed to delete script schedule", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to delete script schedule: " + e.getMessage());
        }
    }

    @GetMapping("/empty-message")
    public ResponseEntity<String> getEmptyMessage() {
        try {
            var model = new HashMap<String, Object>();
            model.put("schedules", schedulerService.loadSchedules());

            TemplateOutput output = new StringOutput();
            templateEngine.render("tools/empty-schedule-message.jte", model, output);
            return ResponseEntity.ok(output.toString());
        } catch (IOException e) {
            log.error("Failed to load schedules", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to check schedules: " + e.getMessage());
        }
    }

    @PostMapping("/execute/{id}")
    public ResponseEntity<Map<String, String>> executeNow(@PathVariable String id) {
        try {
            var displayName = schedulerService.executeNow(id);
            return ResponseEntity.ok(Map.of("displayName", displayName));
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Cannot execute schedule {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to execute schedule {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/edit/{id}")
    public ResponseEntity<String> editSchedule(
            @PathVariable String id,
            @RequestParam String cronExpression,
            @RequestParam String scriptFile) {
        try {
            var updated = schedulerService.updateSchedule(id, cronExpression, scriptFile);
            var model = new HashMap<String, Object>();
            model.put("schedule", updated);
            model.put("lastRan", schedulerService.getLastRanDisplay(updated.scriptFile()));
            model.put("nextRun", schedulerService.getNextRunDisplay(updated.cronExpression()));
            TemplateOutput output = new StringOutput();
            templateEngine.render("tools/schedule-item.jte", model, output);
            return ResponseEntity.ok(output.toString());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to edit schedule {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to edit schedule: " + e.getMessage());
        }
    }

    @GetMapping("/logs")
    public ResponseEntity<List<LogEntry>> listLogs(@RequestParam String scheduleId) {
        try {
            var schedules = schedulerService.loadSchedules();
            var schedule = schedules.stream()
                    .filter(s -> s.id().equals(scheduleId))
                    .findFirst();
            if (schedule.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(schedulerService.listLogs(schedule.get().scriptFile()));
        } catch (Exception e) {
            log.error("Failed to list logs for schedule {}", scheduleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/logs")
    public ResponseEntity<Void> clearLogs(@RequestParam String scheduleId) {
        try {
            var schedules = schedulerService.loadSchedules();
            var schedule = schedules.stream()
                    .filter(s -> s.id().equals(scheduleId))
                    .findFirst();
            if (schedule.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            schedulerService.clearLogs(schedule.get().scriptFile());
            log.info("Cleared logs for schedule {}", scheduleId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to clear logs for schedule {}", scheduleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/log-content")
    public ResponseEntity<String> getLogContent(
            @RequestParam String scheduleId,
            @RequestParam String logFile) {
        try {
            var schedules = schedulerService.loadSchedules();
            var schedule = schedules.stream()
                    .filter(s -> s.id().equals(scheduleId))
                    .findFirst();
            if (schedule.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            var content = schedulerService.readLogContent(schedule.get().scriptFile(), logFile);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(content);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to read log content for schedule {}", scheduleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Expose log-directory name so callers can map scheduleId → script for display
    @GetMapping("/last-ran")
    public ResponseEntity<Map<String, String>> getLastRan(@RequestParam String scheduleId) {
        try {
            var schedules = schedulerService.loadSchedules();
            var schedule = schedules.stream()
                    .filter(s -> s.id().equals(scheduleId))
                    .findFirst();
            if (schedule.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(Map.of("lastRan",
                    schedulerService.getLastRanDisplay(schedule.get().scriptFile())));
        } catch (Exception e) {
            log.error("Failed to get last-ran for schedule {}", scheduleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
