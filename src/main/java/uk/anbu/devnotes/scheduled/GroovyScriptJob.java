package uk.anbu.devnotes.scheduled;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import uk.anbu.devnotes.util.FileUtil;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
public class GroovyScriptJob implements Job {

    private static final String LOG_DIR_NAME = "groovy-script-logs";
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final String GITIGNORE_ENTRY = LOG_DIR_NAME + "/";

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            JobDataMap dataMap = context.getJobDetail().getJobDataMap();
            String scriptFile = FileUtil.cleanDirectoryName(dataMap.getString("scriptFile"));
            String docsRoot = dataMap.getString("docsRoot");

            Path docsDirectory = Path.of(docsRoot);
            Path fullPath = docsDirectory.resolve(scriptFile);

            if (!Files.exists(fullPath)) {
                throw new JobExecutionException("Script file not found: " + fullPath);
            }

            Path logDir = ensureLogDirectory(docsDirectory);

            String scriptContent = Files.readString(fullPath);
            log.info("Executing script: {}", fullPath);

            // Build a timestamped log file name derived from the script file name
            String scriptBaseName = fullPath.getFileName().toString()
                    .replaceAll("[^a-zA-Z0-9._-]", "_");
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            Path logFile = logDir.resolve(scriptBaseName + "_" + timestamp + ".log");

            // Capture stdout and stderr produced by the script
            var stdoutBuf = new ByteArrayOutputStream();
            var stderrBuf = new ByteArrayOutputStream();

            synchronized (GroovyScriptJob.class) {
                PrintStream originalOut = System.out;
                PrintStream originalErr = System.err;
                try (var capturedOut = new PrintStream(stdoutBuf, true, StandardCharsets.UTF_8);
                     var capturedErr = new PrintStream(stderrBuf, true, StandardCharsets.UTF_8)) {
                    System.setOut(capturedOut);
                    System.setErr(capturedErr);
                    try {
                        var binding = new Binding();
                        binding.setProperty("out", capturedOut);
                        binding.setProperty("err", capturedErr);
                        var shell = new GroovyShell(binding);
                        shell.evaluate(scriptContent);
                    } finally {
                        System.setOut(originalOut);
                        System.setErr(originalErr);
                    }
                }
            }

            writeLogFile(logFile, fullPath, timestamp, stdoutBuf, stderrBuf);
            log.info("Script execution completed: {}. Log written to: {}", fullPath, logFile);
        } catch (JobExecutionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to execute script", e);
            throw new JobExecutionException(e);
        }
    }

    private static void writeLogFile(Path logFile, Path scriptPath, String timestamp,
                                     ByteArrayOutputStream stdoutBuf,
                                     ByteArrayOutputStream stderrBuf) throws Exception {
        var logContent = new StringBuilder();
        logContent.append("Script  : ").append(scriptPath).append("\n");
        logContent.append("Executed: ").append(timestamp.replace('_', ' ').replace('-', ':')).append("\n");
        logContent.append("Trigger : Scheduled\n");
        logContent.append("--- STDOUT ---\n");
        logContent.append(stdoutBuf.toString(StandardCharsets.UTF_8));
        logContent.append("\n--- STDERR ---\n");
        logContent.append(stderrBuf.toString(StandardCharsets.UTF_8));
        logContent.append("\n");
        Files.writeString(logFile, logContent, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static Path ensureLogDirectory(Path docsDirectory) throws Exception {
        Path configDirectory = docsDirectory.resolve("config");
        Path logDir = configDirectory.resolve(LOG_DIR_NAME);
        Files.createDirectories(logDir);
        ensureGitIgnoreEntry(configDirectory);
        return logDir;
    }

    private static void ensureGitIgnoreEntry(Path configDirectory) throws Exception {
        Path gitIgnore = configDirectory.resolve(".gitignore");
        if (!Files.exists(gitIgnore)) {
            Files.writeString(gitIgnore, GITIGNORE_ENTRY + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE);
            return;
        }
        String existing = Files.readString(gitIgnore, StandardCharsets.UTF_8);
        boolean alreadyIgnored = existing.lines()
                .map(String::trim)
                .anyMatch(line -> line.equals(GITIGNORE_ENTRY) || line.equals(LOG_DIR_NAME));
        if (!alreadyIgnored) {
            String appended = existing.endsWith("\n")
                    ? existing + GITIGNORE_ENTRY + "\n"
                    : existing + "\n" + GITIGNORE_ENTRY + "\n";
            Files.writeString(gitIgnore, appended, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING);
        }
    }
}