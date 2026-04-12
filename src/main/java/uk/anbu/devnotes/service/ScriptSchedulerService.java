package uk.anbu.devnotes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.stereotype.Service;
import uk.anbu.devnotes.scheduled.GroovyScriptJob;
import uk.anbu.devnotes.types.LogEntry;
import uk.anbu.devnotes.types.ScriptSchedule;
import uk.anbu.devnotes.util.FileUtil;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ScriptSchedulerService {
    private static final String CRON_FILENAME = "cron.yaml";
    private static final String LOG_DIR_NAME = "groovy-script-logs";
    private static final DateTimeFormatter LOG_FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter DISPLAY_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ConfigService configService;
    private final Scheduler scheduler;

    public ScriptSchedulerService(ConfigService configService) throws SchedulerException {
        this.configService = configService;
        SchedulerFactory schedulerFactory = new StdSchedulerFactory();
        this.scheduler = schedulerFactory.getScheduler();
        this.scheduler.start();
        loadAndScheduleAllJobs();
    }

    private void loadAndScheduleAllJobs() {
        try {
            List<ScriptSchedule> schedules = loadSchedules();
            for (ScriptSchedule schedule : schedules) {
                scheduleJob(schedule);
            }
        } catch (IOException | SchedulerException e) {
            log.error("Failed to load and schedule jobs", e);
        }
    }

    public List<ScriptSchedule> loadSchedules() throws IOException {
        Path cronPath = getCronFilePath(configService.getDocsDirectory());
        if (!Files.exists(cronPath)) {
            saveSchedules(new ArrayList<>());
            return new ArrayList<>();
        }

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());
        return mapper.readValue(cronPath.toFile(),
                mapper.getTypeFactory().constructCollectionType(List.class, ScriptSchedule.class));
    }

    public void saveSchedules(List<ScriptSchedule> schedules) throws IOException {
        Path cronPath = getCronFilePath(configService.getDocsDirectory());
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());
        mapper.writeValue(cronPath.toFile(), schedules);
    }

    private static Path getCronFilePath(String docsDirectory) throws IOException {
        Path docsDirectoryPath = Path.of(docsDirectory);
        Path configDirectory = docsDirectoryPath.resolve("config");
        Path cronPath = configDirectory.resolve(CRON_FILENAME);

        Files.createDirectories(configDirectory);
        return cronPath;
    }

    public void scheduleJob(ScriptSchedule schedule) throws SchedulerException {
        JobDetail job = JobBuilder.newJob(GroovyScriptJob.class)
                .withIdentity(schedule.id())
                .usingJobData("scriptFile", schedule.scriptFile())
                .usingJobData("docsRoot", configService.getDocsDirectory())
                .build();

        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(schedule.id())
                .withSchedule(CronScheduleBuilder.cronSchedule(schedule.cronExpression()))
                .build();

        scheduler.scheduleJob(job, trigger);
    }

    public boolean deleteJob(String id) throws SchedulerException {
        return scheduler.deleteJob(new JobKey(id));
    }

    /**
     * Returns the next scheduled fire time for the given cron expression as a human-readable
     * string, or {@code "Invalid cron"} / {@code "Never"} when appropriate.
     */
    public String getNextRunDisplay(String cronExpression) {
        try {
            var cronExpr = new org.quartz.CronExpression(cronExpression);
            var next = cronExpr.getNextValidTimeAfter(new Date());
            if (next == null) return "Never";
            return LocalDateTime.ofInstant(next.toInstant(), ZoneId.systemDefault())
                    .format(DISPLAY_TIMESTAMP);
        } catch (Exception e) {
            return "Invalid cron";
        }
    }

    public ScriptSchedule updateSchedule(String id, String cronExpression, String scriptFile)
            throws IOException, SchedulerException {
        var schedules = loadSchedules();
        var updated = new ScriptSchedule(id, cronExpression, scriptFile);
        var replaced = schedules.stream().anyMatch(s -> s.id().equals(id));
        if (!replaced) {
            throw new IllegalArgumentException("Schedule not found: " + id);
        }
        schedules.replaceAll(s -> s.id().equals(id) ? updated : s);
        saveSchedules(schedules);
        deleteJob(id);
        scheduleJob(updated);
        return updated;
    }

    /**
     * Executes the script for the given schedule immediately, captures stdout/stderr to a
     * timestamped log file, and returns the human-readable run timestamp.
     * Script exceptions are written to the log rather than propagated, so the log always exists
     * after this method returns successfully.
     */
    public String executeNow(String id) throws Exception {
        var schedules = loadSchedules();
        var schedule = schedules.stream()
                .filter(s -> s.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + id));

        var scriptFile = FileUtil.cleanDirectoryName(schedule.scriptFile());
        var docsDirectory = Path.of(configService.getDocsDirectory());
        var fullPath = docsDirectory.resolve(scriptFile);

        if (!Files.exists(fullPath)) {
            throw new IllegalStateException("Script file not found: " + fullPath);
        }

        var logDir = getLogDirectory();
        var scriptContent = Files.readString(fullPath);

        var scriptBaseName = fullPath.getFileName().toString()
                .replaceAll("[^a-zA-Z0-9._-]", "_");
        var timestamp = LocalDateTime.now().format(LOG_FILE_TIMESTAMP);
        var logFileName = scriptBaseName + "_" + timestamp + ".log";
        var logFile = logDir.resolve(logFileName);

        var stdoutBuf = new ByteArrayOutputStream();
        var stderrBuf = new ByteArrayOutputStream();

        // Synchronize on GroovyScriptJob.class so scheduled and manual runs never overlap
        synchronized (GroovyScriptJob.class) {
            var originalOut = System.out;
            var originalErr = System.err;
            try (var capturedOut = new PrintStream(stdoutBuf, true, StandardCharsets.UTF_8);
                 var capturedErr = new PrintStream(stderrBuf, true, StandardCharsets.UTF_8)) {
                System.setOut(capturedOut);
                System.setErr(capturedErr);
                try {
                    var binding = new Binding();
                    binding.setProperty("out", capturedOut);
                    binding.setProperty("err", capturedErr);
                    new GroovyShell(binding).evaluate(scriptContent);
                } catch (Exception e) {
                    e.printStackTrace(capturedErr);   // capture – don't propagate
                } finally {
                    System.setOut(originalOut);
                    System.setErr(originalErr);
                }
            }
        }

        var logContent = new StringBuilder();
        logContent.append("Script  : ").append(fullPath).append("\n");
        logContent.append("Executed: ").append(timestamp.replace('_', ' ').replace('-', ':')).append("\n");
        logContent.append("Trigger : Manual\n");
        logContent.append("--- STDOUT ---\n");
        logContent.append(stdoutBuf.toString(StandardCharsets.UTF_8));
        logContent.append("\n--- STDERR ---\n");
        logContent.append(stderrBuf.toString(StandardCharsets.UTF_8));
        logContent.append("\n");
        Files.writeString(logFile, logContent, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        log.info("Manual execution completed: {}. Log: {}", fullPath, logFile);
        return parseDisplayTimestamp(scriptBaseName, logFileName);
    }

    // ── Log helpers ──────────────────────────────────────────────────────────

    private Path getLogDirectory() throws IOException {
        var logDir = Path.of(configService.getDocsDirectory())
                .resolve("config").resolve(LOG_DIR_NAME);
        Files.createDirectories(logDir);
        return logDir;
    }

    /** Derives the sanitised filename prefix used by {@link uk.anbu.devnotes.scheduled.GroovyScriptJob}. */
    private static String scriptBaseName(String scriptFile) {
        return Path.of(scriptFile).getFileName().toString()
                .replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String parseDisplayTimestamp(String baseName, String logFileName) {
        var prefix = baseName + "_";
        if (logFileName.startsWith(prefix) && logFileName.endsWith(".log")) {
            var ts = logFileName.substring(prefix.length(), logFileName.length() - 4);
            try {
                return LocalDateTime.parse(ts, LOG_FILE_TIMESTAMP).format(DISPLAY_TIMESTAMP);
            } catch (Exception ignored) {
                return ts;
            }
        }
        return logFileName;
    }

    /** Deletes every log file that belongs to the given script. */
    public void clearLogs(String scriptFile) throws IOException {
        var logDir = getLogDirectory();
        var baseName = scriptBaseName(scriptFile);
        try (var stream = Files.list(logDir)) {
            stream.filter(p -> {
                var n = p.getFileName().toString();
                return n.startsWith(baseName + "_") && n.endsWith(".log");
            }).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    log.warn("Failed to delete log file {}", p, e);
                }
            });
        }
    }

    /** Returns a human-readable timestamp of the last execution, or {@code "Never"}. */
    public String getLastRanDisplay(String scriptFile) {
        try {
            var logDir = getLogDirectory();
            var baseName = scriptBaseName(scriptFile);
            try (var stream = Files.list(logDir)) {
                return stream
                        .filter(p -> {
                            var n = p.getFileName().toString();
                            return n.startsWith(baseName + "_") && n.endsWith(".log");
                        })
                        .map(p -> p.getFileName().toString())
                        .max(Comparator.naturalOrder())
                        .map(n -> parseDisplayTimestamp(baseName, n))
                        .orElse("Never");
            }
        } catch (IOException e) {
            log.warn("Failed to determine last-ran time for script {}", scriptFile, e);
            return "Never";
        }
    }

    /** Returns all log entries for the given script file, newest first. */
    public List<LogEntry> listLogs(String scriptFile) throws IOException {
        var logDir = getLogDirectory();
        var baseName = scriptBaseName(scriptFile);
        try (var stream = Files.list(logDir)) {
            return stream
                    .filter(p -> {
                        var n = p.getFileName().toString();
                        return n.startsWith(baseName + "_") && n.endsWith(".log");
                    })
                    .map(p -> p.getFileName().toString())
                    .sorted(Comparator.reverseOrder())
                    .map(n -> new LogEntry(n, parseDisplayTimestamp(baseName, n)))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Reads the content of a log file, verifying it belongs to the given script.
     * Throws {@link IllegalArgumentException} if the filename does not match the expected pattern.
     */
    public String readLogContent(String scriptFile, String logFileName) throws IOException {
        var safeName = Path.of(logFileName).getFileName().toString();
        var baseName = scriptBaseName(scriptFile);
        if (!safeName.startsWith(baseName + "_") || !safeName.endsWith(".log")) {
            throw new IllegalArgumentException("Log file does not match script: " + logFileName);
        }
        var logFile = getLogDirectory().resolve(safeName);
        if (!Files.exists(logFile)) {
            return "(Log file not found)";
        }
        return Files.readString(logFile, StandardCharsets.UTF_8);
    }

}