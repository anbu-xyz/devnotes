package uk.anbu.devnotes.controller;

import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devnotes.module.MarkdownRenderer;
import uk.anbu.devnotes.service.ConfigService;
import uk.anbu.devnotes.util.FileUtil;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static uk.anbu.devnotes.controller.ImageController.isImage;

@Slf4j
@RequiredArgsConstructor
@RestController
public class MarkdownController {

    private final MarkdownRenderer markdownRenderer;

    private final TemplateEngine templateEngine;

    private final ConfigService configService;

    @GetMapping("/markdown")
    public ResponseEntity<Object> markdown(@RequestParam(name = "filename", required = false) String filename,
                                           @RequestParam(name = "edit", required = false, defaultValue = "false") boolean editMode) throws IOException {
        try {
            if (filename == null) {
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, "/markdown?filename=index.md")
                        .build();
            }

            if (filename.equals("/")) {
                filename = ".";
            } else if (filename.startsWith("/")) {
                filename = filename.substring(1);
            }

            Path markdownRoot = Paths.get(configService.getDocsDirectory());
            Path filePath;
            try {
                filePath = markdownRoot.resolve(filename);
            } catch (InvalidPathException e) {
                log.error("Invalid path: {}", filename);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Invalid path " + filename);
            }

            String fileExtension = FileUtil.getFileExtension(filename).toLowerCase();
            if (Files.isDirectory(filePath)) {
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, "/renderDirectoryContents?directoryName=" + filename)
                        .build();
            } else if (!filePath.toFile().exists() && fileExtension.equals("md")) {
                return handleMissingMarkdownFile(filename);
            } else {
                return readFileContent(filename, fileExtension, markdownRoot, editMode);
            }
        } catch (Exception e) {
            log.error("Error rendering markdown", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error rendering markdown: " + e.getMessage());
        }
    }

    private ResponseEntity<Object> handleMissingMarkdownFile(String filename) {
        var model = Map.of("filename", filename);
        TemplateOutput output = new StringOutput();
        templateEngine.render("file-not-found.jte", model, output);
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.TEXT_HTML)
                .body(output.toString());
    }

    private ResponseEntity<Object> readFileContent(String filename, String fileExtension, Path markdownRoot, boolean editMode) throws IOException {
        if ("md".equals(fileExtension)) {
            var content = fetchMarkdownContent(filename, editMode);
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.TEXT_HTML)
                    .body(content.content());
        } else if (isImage(fileExtension)) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "/image?filename=" + filename)
                    .build();
        } else {
            // For other text files, set content type to plain text
            String content = Files.readString(markdownRoot.resolve(filename), StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                    .body(content);
        }
    }

    @PostMapping("/createNewMarkdown")
    public ResponseEntity<String> createNewMarkdown(@RequestParam String filename) {
        try {
            Path markdownRoot = Paths.get(configService.getDocsDirectory());
            Path filePath = markdownRoot.resolve(filename);
            if (!Files.exists(filePath)) {
                Files.createDirectories(filePath.getParent());
                Files.createFile(filePath);
            }
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "/markdown?filename=" + filename)
                    .build();
        } catch (Exception e) {
            log.error("Error creating markdown file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating file");
        }
    }

    public record ContentWithType(String content, String contentType) {
    }

    public ContentWithType fetchMarkdownContent(String filename, boolean editMode) throws IOException {
        Assert.isTrue(filename.endsWith(".md"), "filename must end with .md");
        Path markdownRoot = Paths.get(configService.getDocsDirectory());
        log.info("Fetching file: {}", filename);
        var markdownFile = markdownRoot.resolve(filename);
        Assert.isTrue(markdownFile.toFile().exists(), "File does not exist " + filename);

        log.info("Rendering markdown: {}", markdownFile);
        String markdownContent = new String(Files.readAllBytes(markdownFile));
        String originalMarkdown = getOriginalMarkdown(markdownFile);
        String htmlContent = markdownRenderer.convertMarkdown(markdownContent, filename);

        TemplateOutput output = new StringOutput();
        var params = new HashMap<String, Object>();
        params.put("htmlContent", htmlContent);
        params.put("markdownFile", markdownFile);
        params.put("originalMarkdown", escapeHtml(originalMarkdown));
        params.put("editMode", editMode);
        templateEngine.render("markdown.jte", params, output);

        return new ContentWithType(output.toString(), "text/html");
    }

    private String getOriginalMarkdown(Path filename) {
        try {
            return new String(Files.readAllBytes(filename));
        } catch (IOException e) {
            log.error("Error reading markdown file", e);
            return "";
        }
    }

    private String escapeHtml(String html) {
        return html.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    @PostMapping("/saveMarkdown")
    public ResponseEntity<String> saveMarkdown(@RequestParam(name = "filename", required = false) String filename,
                                               @RequestParam(name = "edit", required = false, defaultValue = "false") boolean editMode,
                                               @RequestBody String content) {
        try {
            var decodedFilename = URLDecoder.decode(filename, StandardCharsets.UTF_8);
            Path filePath = Paths.get(configService.getDocsDirectory(), decodedFilename);
            Files.write(filePath, content.getBytes());
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "/markdown?filename=" + decodedFilename + "&edit=false")
                    .build();
        } catch (Exception e) {
            log.error("Error saving markdown file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error saving file");
        }
    }
}