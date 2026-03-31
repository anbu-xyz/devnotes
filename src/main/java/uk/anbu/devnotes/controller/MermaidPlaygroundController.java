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
import uk.anbu.devnotes.service.ConfigService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@RestController
public class MermaidPlaygroundController {

    private static final String AUTOSAVE_RELATIVE_PATH = "config/mermaid/last-edited.mmd";

    private static final String DEFAULT_CONTENT = """
            graph TD
                A[Client] --> B[Load Balancer]
                B --> C[Server 1]
                B --> D[Server 2]

                click A "https://example.com" "Go to example"
            """;

    private final TemplateEngine templateEngine;
    private final ConfigService configService;

    @GetMapping("/mermaid-playground")
    public ResponseEntity<String> mermaidPlayground() {
        try {
            var autosavePath = autosavePath();
            var initialContent = Files.exists(autosavePath)
                    ? Files.readString(autosavePath, StandardCharsets.UTF_8)
                    : DEFAULT_CONTENT;

            TemplateOutput output = new StringOutput();
            templateEngine.render("render/mermaid-playground.jte", Map.of("initialContent", initialContent), output);

            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(output.toString());
        } catch (Exception e) {
            log.error("Error rendering mermaid playground", e);
            return ResponseEntity.internalServerError()
                    .body("Error rendering mermaid playground: " + e.getMessage());
        }
    }

    @PostMapping(value = "/mermaid-playground/autosave", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<Void> autosave(@RequestBody String content) {
        try {
            var path = autosavePath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error autosaving mermaid playground content", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(value = "/mermaid-playground/save", consumes = MediaType.TEXT_PLAIN_VALUE)
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
            log.error("Error saving mermaid content to {}", savePath, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private Path autosavePath() {
        return Paths.get(configService.getDocsDirectory()).resolve(AUTOSAVE_RELATIVE_PATH);
    }
}