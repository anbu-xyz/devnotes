package uk.anbu.devtools.controller;

import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devtools.module.ImageRenderer;
import uk.anbu.devtools.module.MarkdownRenderer;
import uk.anbu.devtools.service.ConfigService;
import uk.anbu.devtools.util.FileUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@RestController
public class MarkdownController {

    private final MarkdownRenderer markdownRenderer;

    private final ImageRenderer imageRenderer;

    private final TemplateEngine templateEngine;

    private final ConfigService configService;

    @GetMapping("/")
    public ResponseEntity<Object> index() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "/markdown?filename=index.md")
                .build();
    }

    @GetMapping("/markdown")
    public Object markdown(@RequestParam(name = "filename", required = false) String filename) throws IOException {
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

        Path markdownRoot = Paths.get(configService.getMarkdownDirectory());
        Path filePath = markdownRoot.resolve(filename);

        if (Files.isDirectory(filePath)) {
            return renderDirectoryContents(filename, markdownRoot.resolve(filename));
        } else {
            var content = fetchMarkdownContent(filename);
            if (content.isEmpty()) {
                var model = Map.of("filename", filename);
                TemplateOutput output = new StringOutput();
                templateEngine.render("file-not-found.jte", model, output);
                return output.toString();
            } else {
                return content.get();
            }
        }
    }

    private String renderDirectoryContents(String directoryName, Path filePath) throws IOException {
        try (var filesList = Files.list(filePath)) {
            var entries = filesList
                    .map(path -> new FileEntry(path.getFileName().toString(), Files.isDirectory(path)))
                    .sorted(Comparator.<FileEntry>comparingInt(e -> e.isDirectory() ? 0 : 1)
                            .thenComparing(f -> f.filename))
                    .toList();

            var model = Map.of(
                    "directoryName", directoryName,
                    "entries", entries
            );
            TemplateOutput output = new StringOutput();
            templateEngine.render("directory-listing.jte", model, output);
            return output.toString();
        }
    }

    public record FileEntry(String filename, boolean isDirectory) {
    }

    @PostMapping("/createNewMarkdown")
    public ResponseEntity<String> createNewMarkdown(@RequestParam String filename) {
        try {
            Path markdownRoot = Paths.get(configService.getMarkdownDirectory());
            Path filePath = markdownRoot.resolve(filename);
            if (!Files.exists(filePath)) {
                Files.createDirectories(filePath.getParent());
                Files.createFile(filePath);
            }
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "/markdown?filename=" + filename)
                    .build();
        } catch (IOException e) {
            log.error("Error creating markdown file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating file");
        }
    }

    public Optional<String> fetchMarkdownContent(String filename) throws IOException {
        Path markdownRoot = Paths.get(configService.getMarkdownDirectory());
        log.info("Fetching file: {}", filename);
        var markdownFile = markdownRoot.resolve(filename);
        if (markdownFile.toFile().exists()) {
            if ("md".equalsIgnoreCase(FileUtil.getFileExtension(filename))) {
                log.info("Rendering markdown: {}", markdownFile);
                String markdownContent = new String(Files.readAllBytes(markdownFile));
                String originalMarkdown = getOriginalMarkdown(markdownFile);
                var htmlContent = markdownRenderer.convertMarkdown(markdownContent);

                TemplateOutput output = new StringOutput();
                var page = Map.of("htmlContent", htmlContent,"filename", markdownFile.getFileName().toString(), "originalMarkdown", escapeHtml(originalMarkdown));
                templateEngine.render("markdown-wrapper.jte", page, output);

                return Optional.of(output.toString());
            } else {
                // assume text file
                return Optional.of(Files.readString(markdownRoot.resolve(filename), StandardCharsets.UTF_8));
            }
        } else {
            return Optional.empty();
        }
    }

    @GetMapping("/image")
    public ResponseEntity<Resource> image(@RequestParam(name = "path", required = false) String path,
                                          @RequestParam(name = "filename") String filename) {
        try {
            var resource = imageRenderer.image(path, filename);

            if (resource.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            String contentType = "image/" + FileUtil.getFileExtension(filename).toLowerCase();

            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource.get());

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
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
    public ResponseEntity<String> saveMarkdown(@RequestParam String filename, @RequestBody String content) {
        try {
            Path filePath = Paths.get(configService.getMarkdownDirectory(), filename);
            Files.write(filePath, content.getBytes());
            return ResponseEntity.ok("Saved successfully");
        } catch (IOException e) {
            log.error("Error saving markdown file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error saving file");
        }
    }

    @PostMapping("/createSubdirectory")
    public ResponseEntity<String> createSubdirectory(@RequestParam String path, @RequestParam String name) {
        try {
            Path markdownRoot = Paths.get(configService.getMarkdownDirectory());
            Path newDirPath = markdownRoot.resolve(path).resolve(name);
            Files.createDirectories(newDirPath);
            return ResponseEntity.ok("Subdirectory created successfully");
        } catch (IOException e) {
            log.error("Error creating subdirectory", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating subdirectory");
        }
    }

    @PostMapping("/createMarkdown")
    public ResponseEntity<String> createMarkdown(@RequestParam String path, @RequestParam String name) {
        try {
            Path markdownRoot = Paths.get(configService.getMarkdownDirectory());
            Path newFilePath = markdownRoot.resolve(path).resolve(name);
            Files.createFile(newFilePath);
            return ResponseEntity.ok("Markdown file created successfully");
        } catch (IOException e) {
            log.error("Error creating markdown file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating markdown file");
        }
    }

    @PostMapping("/renameEntry")
    public ResponseEntity<String> renameEntry(@RequestParam String path, @RequestParam String oldName, @RequestParam String newName) {
        try {
            Path markdownRoot = Paths.get(configService.getMarkdownDirectory());
            Path oldPath = markdownRoot.resolve(path).resolve(oldName);
            Path newPath = markdownRoot.resolve(path).resolve(newName);
            Files.move(oldPath, newPath);
            return ResponseEntity.ok("Entry renamed successfully");
        } catch (IOException e) {
            log.error("Error renaming entry", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error renaming entry");
        }
    }

    @PostMapping("/deleteEntry")
    public ResponseEntity<String> deleteEntry(@RequestParam String path, @RequestParam String name) {
        try {
            Path markdownRoot = Paths.get(configService.getMarkdownDirectory());
            Path entryPath = markdownRoot.resolve(path).resolve(name);
            if (Files.isDirectory(entryPath)) {
                FileUtils.deleteDirectory(entryPath.toFile());
            } else {
                Files.delete(entryPath);
            }
            return ResponseEntity.ok("Entry deleted successfully");
        } catch (IOException e) {
            log.error("Error deleting entry", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting entry");
        }
    }
}