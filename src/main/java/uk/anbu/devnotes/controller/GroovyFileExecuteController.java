package uk.anbu.devnotes.controller;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devnotes.scheduled.GroovyScriptJob;
import uk.anbu.devnotes.service.ConfigService;
import uk.anbu.devnotes.util.FileUtil;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

@Slf4j
@RequiredArgsConstructor
@RestController
public class GroovyFileExecuteController {

    private final ConfigService configService;

    public record ExecuteResult(String stdout, String stderr) {}

    @PostMapping("/groovy/execute-file")
    public ResponseEntity<ExecuteResult> executeFile(
            @RequestParam String path,
            @RequestParam String name) {
        try {
            var docsRoot = Paths.get(configService.getDocsDirectory()).normalize();
            var cleanPath = FileUtil.cleanDirectoryName(path);
            var scriptPath = docsRoot.resolve(cleanPath).resolve(name).normalize();

            if (!scriptPath.startsWith(docsRoot)) {
                return ResponseEntity.badRequest()
                        .body(new ExecuteResult("", "Invalid path: access denied"));
            }

            if (!Files.exists(scriptPath)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ExecuteResult("", "Script file not found: " + name));
            }

            var scriptContent = Files.readString(scriptPath, StandardCharsets.UTF_8);
            var stdoutBuf = new ByteArrayOutputStream();
            var stderrBuf = new ByteArrayOutputStream();

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
                        e.printStackTrace(capturedErr);
                    } finally {
                        System.setOut(originalOut);
                        System.setErr(originalErr);
                    }
                }
            }

            return ResponseEntity.ok(new ExecuteResult(
                    stdoutBuf.toString(StandardCharsets.UTF_8),
                    stderrBuf.toString(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.error("Error executing Groovy file {}/{}", path, name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ExecuteResult("", e.getMessage()));
        }
    }
}