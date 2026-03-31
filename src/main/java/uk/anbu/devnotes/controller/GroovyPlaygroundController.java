package uk.anbu.devnotes.controller;

import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devnotes.module.GroovyRenderer;
import uk.anbu.devnotes.service.ConfigService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@RestController
public class GroovyPlaygroundController {

    private static final String AUTOSAVE_SCRIPT_PATH = "config/groovy-playground/last-edited.groovy";
    private static final String AUTOSAVE_MODE_PATH = "config/groovy-playground/last-render-mode.txt";

    private static final String DEFAULT_SCRIPT = """
            def output = "N, N squared\\n"
            for (int i = 0; i < 10; i++) {
                output += i + "," + (i * i) + "\\n"
            }
            output
            """;

    private static final String DEFAULT_RENDER_MODE = "csv-table-with-header";

    private final TemplateEngine templateEngine;
    private final ConfigService configService;

    @GetMapping("/groovy-playground")
    public ResponseEntity<String> groovyPlayground() {
        try {
            var scriptPath = autosavePath(AUTOSAVE_SCRIPT_PATH);
            var modePath = autosavePath(AUTOSAVE_MODE_PATH);

            var initialContent = Files.exists(scriptPath)
                    ? Files.readString(scriptPath, StandardCharsets.UTF_8)
                    : DEFAULT_SCRIPT;
            var initialRenderMode = Files.exists(modePath)
                    ? Files.readString(modePath, StandardCharsets.UTF_8).strip()
                    : DEFAULT_RENDER_MODE;

            TemplateOutput output = new StringOutput();
            templateEngine.render("render/groovy-playground.jte",
                    Map.of("initialContent", initialContent, "initialRenderMode", initialRenderMode),
                    output);

            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(output.toString());
        } catch (Exception e) {
            log.error("Error rendering groovy playground", e);
            return ResponseEntity.internalServerError()
                    .body("Error rendering groovy playground: " + e.getMessage());
        }
    }

    @PostMapping(value = "/groovy-playground/autosave", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> autosave(@RequestBody Map<String, String> body) {
        try {
            var script = body.getOrDefault("script", "");
            var renderMode = body.getOrDefault("renderMode", DEFAULT_RENDER_MODE);

            var scriptPath = autosavePath(AUTOSAVE_SCRIPT_PATH);
            Files.createDirectories(scriptPath.getParent());
            Files.writeString(scriptPath, script, StandardCharsets.UTF_8);

            var modePath = autosavePath(AUTOSAVE_MODE_PATH);
            Files.writeString(modePath, renderMode, StandardCharsets.UTF_8);

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error autosaving groovy playground content", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(value = "/groovy-playground/save", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<Void> save(@RequestParam String savePath, @RequestBody String content) {
        try {
            var docsRoot = Paths.get(configService.getDocsDirectory()).toAbsolutePath().normalize();
            var targetPath = docsRoot.resolve(savePath).normalize();
            if (!targetPath.startsWith(docsRoot)) {
                log.warn("Rejected path traversal attempt: {}", savePath);
                return ResponseEntity.badRequest().build();
            }
            Files.createDirectories(targetPath.getParent());
            Files.writeString(targetPath, content, StandardCharsets.UTF_8);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error saving groovy content to {}", savePath, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(value = "/groovy-playground/execute",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> execute(@RequestBody Map<String, String> body) {
        var script = body.getOrDefault("script", "");
        var renderMode = body.getOrDefault("renderMode", "text");
        var renderer = new GroovyRenderer(configService::getChromeDriverLocation);
        var html = renderer.executeToHtml(script, renderMode);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    private Path autosavePath(String relativePath) {
        return Paths.get(configService.getDocsDirectory()).resolve(relativePath);
    }
}

