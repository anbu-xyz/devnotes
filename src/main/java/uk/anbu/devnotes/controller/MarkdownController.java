package uk.anbu.devnotes.controller;

import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devnotes.module.MarkdownRenderer;
import uk.anbu.devnotes.service.ConfigService;
import uk.anbu.devnotes.types.Markdown;
import uk.anbu.devnotes.types.MarkdownFile;
import uk.anbu.devnotes.util.FileUtil;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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


    @GetMapping("/markdownViewer")
    public ResponseEntity<String> markdownViewer(@RequestParam String filename,
                                                 @RequestParam Map<String, String> allRequestParams) {
        try {
            Path markdownRoot = Paths.get(configService.getDocsDirectory());
            var markdownFile = new MarkdownFile(markdownRoot, filename);
            Assert.isTrue(markdownFile.exists(), "File does not exist " + filename);
            var markdown = new Markdown(Files.readAllBytes(markdownFile.fullPath()));
            String htmlContent = markdownRenderer.convertMarkdown(markdown, markdownFile, allRequestParams);

            TemplateOutput output = new StringOutput();
            var params = new HashMap<String, Object>();
            params.put("htmlContent", htmlContent);
            params.put("title", constructMarkdownTitle(filename, markdownRoot));
            params.put("lastModifiedTime", lastModifiedTime(markdownFile.fullPath()));
            params.put("markdownFile", filename);
            templateEngine.render("render/markdown-viewer.jte", params, output);

            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.TEXT_HTML)
                    .body(output.toString());
        } catch (Exception e) {
            log.error("Error fetching raw markdown", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error fetching raw markdown: " + e.getMessage());
        }
    }

    @GetMapping("/markdownEditor")
    public ResponseEntity<String> markdownEditor(@RequestParam String filename) {
        try {
            Path markdownRoot = Paths.get(configService.getDocsDirectory());
            Path filePath = markdownRoot.resolve(filename);
            Assert.isTrue(filePath.toFile().exists(), "File does not exist " + filename);
            String fileContent = new String(Files.readAllBytes(filePath));

            TemplateOutput output = new StringOutput();
            var params = new HashMap<String, Object>();
            params.put("originalMarkdown", escapeHtml(fileContent));
            params.put("lastModifiedTime", lastModifiedTime(filePath));
            params.put("title", constructMarkdownTitle(filename, markdownRoot));
            templateEngine.render("render/markdown-editor.jte", params, output);

            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.TEXT_HTML)
                    .body(output.toString());
        } catch (Exception e) {
            log.error("Error fetching raw markdown", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error fetching raw markdown: " + e.getMessage());
        }
    }

    private static String lastModifiedTime(Path filePath) throws IOException {
        var lastModifiedTime = Files.getLastModifiedTime(filePath);
        return lastModifiedTime.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @GetMapping("/markdown")
    public ResponseEntity<Object> markdown(@RequestParam(name = "filename", required = false) String filename,
                                           @RequestParam Map<String, String> allRequestParams,
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

            // Build a map of query params to pass to renderer, excluding internal params
            var queryParams = new HashMap<String, Object>();
            if (allRequestParams != null) {
                for (var entry : allRequestParams.entrySet()) {
                    String k = entry.getKey();
                    if ("filename".equals(k) || "edit".equals(k)) continue;
                    queryParams.put(k, entry.getValue());
                }
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
                return readFileContent(filename, fileExtension, markdownRoot, editMode, queryParams);
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

    private ResponseEntity<Object> readFileContent(String filename, String fileExtension,
                                                   Path markdownRoot, boolean editMode, Map<String, Object> queryParams) throws IOException {
        if ("md".equals(fileExtension)) {
            var content = fetchMarkdownContent(filename, markdownRoot, editMode, queryParams);
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.TEXT_HTML)
                    .body(content.content());
        } else if (isImage(fileExtension)) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "/image?filename=" + filename)
                    .build();
        } else if (isYaml(fileExtension)) {
            var content = fetchYamlContent(filename, markdownRoot);
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.TEXT_HTML)
                    .body(content.content());
        } else if (isGroovy(fileExtension)) {
            var content = fetchGroovyContent(filename, markdownRoot);
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.TEXT_HTML)
                    .body(content.content());
        } else if (isMermaid(fileExtension)) {
            var content = fetchMermaidContent(filename, markdownRoot);
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.TEXT_HTML)
                    .body(content.content());
        } else if (isJavascript(fileExtension)) {
            var content = fetchJavascriptContent(filename, markdownRoot);
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.TEXT_HTML)
                    .body(content.content());
        } else if (isSql(fileExtension)) {
            var content = fetchSqlContent(filename, markdownRoot);
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.TEXT_HTML)
                    .body(content.content());
        } else if (isPlantuml(fileExtension)) {
            var content = fetchPlantumlContent(filename);
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.TEXT_HTML)
                    .body(content.content());
        } else {
            // For other text files, set content type to plain text
            String content = Files.readString(markdownRoot.resolve(filename), StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                    .body(content);
        }
    }

    private boolean isGroovy(String fileExtension) {
        return "groovy".equals(fileExtension);
    }

    private boolean isMermaid(String fileExtension) {
        return "mermaid".equals(fileExtension) || "mmd".equals(fileExtension);
    }

    private boolean isYaml(String fileExtension) {
        return "yaml".equals(fileExtension);
    }

    @SneakyThrows
    private ContentWithType fetchPlantumlContent(String filename) {
        TemplateOutput output = new StringOutput();
        var params = new HashMap<String, Object>();
        params.put("plantumlFile", filename);
        params.put("title", filename);
        templateEngine.render("render/plantuml.jte", params, output);

        return new ContentWithType(output.toString(), "text/html");
    }

    private boolean isPlantuml(String fileExtension) {
        return "puml".equals(fileExtension);
    }

    private ContentWithType fetchJavascriptContent(String filename, Path markdownRoot) throws IOException {
        String fileContent = new String(Files.readAllBytes(markdownRoot.resolve(filename)));

        TemplateOutput output = new StringOutput();
        var params = new HashMap<String, Object>();
        params.put("javascriptContent", fileContent);
        params.put("title", filename);
        templateEngine.render("render/javascript.jte", params, output);

        return new ContentWithType(output.toString(), "text/html");
    }

    private ContentWithType fetchSqlContent(String filename, Path markdownRoot) throws IOException {
        String fileContent = new String(Files.readAllBytes(markdownRoot.resolve(filename)));

        TemplateOutput output = new StringOutput();
        var params = new HashMap<String, Object>();
        params.put("sqlContent", fileContent);
        params.put("title", filename);
        templateEngine.render("render/sql.jte", params, output);

        return new ContentWithType(output.toString(), "text/html");
    }

    private ContentWithType fetchMermaidContent(String filename, Path markdownRoot) throws IOException {
        String fileContent = new String(Files.readAllBytes(markdownRoot.resolve(filename)));

        TemplateOutput output = new StringOutput();
        var params = new HashMap<String, Object>();
        params.put("mermaidCodeContent", fileContent);
        params.put("title", filename);
        templateEngine.render("render/mermaid.jte", params, output);

        return new ContentWithType(output.toString(), "text/html");
    }

    private ContentWithType fetchGroovyContent(String filename, Path markdownRoot) throws IOException {
        String fileContent = new String(Files.readAllBytes(markdownRoot.resolve(filename)));

        TemplateOutput output = new StringOutput();
        var params = new HashMap<String, Object>();
        params.put("groovyCodeContent", fileContent);
        params.put("title", filename);
        templateEngine.render("render/groovy.jte", params, output);

        return new ContentWithType(output.toString(), "text/html");
    }

    private ContentWithType fetchYamlContent(String filename, Path markdownRoot) throws IOException {
        String fileContent = new String(Files.readAllBytes(markdownRoot.resolve(filename)));

        TemplateOutput output = new StringOutput();
        var params = new HashMap<String, Object>();
        params.put("yamlCodeContent", fileContent);
        params.put("title", filename);
        templateEngine.render("render/yaml.jte", params, output);

        return new ContentWithType(output.toString(), "text/html");
    }

    private boolean isJavascript(String fileExtension) {
        return "js".equalsIgnoreCase(fileExtension) || "mjs".equalsIgnoreCase(fileExtension);
    }

    private boolean isSql(String fileExtension) {
        return "sql".equalsIgnoreCase(fileExtension);
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

    public ContentWithType fetchMarkdownContent(String filename,
                                                Path markdownRoot, boolean editMode,
                                                Map<String, Object> queryParams) throws IOException {
        Assert.isTrue(filename.endsWith(".md"), "filename must end with .md");
        log.info("Fetching file: {}", filename);
        var markdownFile = markdownRoot.resolve(filename);
        Assert.isTrue(markdownFile.toFile().exists(), "File does not exist " + filename);

        String title = constructMarkdownTitle(filename, markdownRoot);

        TemplateOutput output = new StringOutput();
        var params = new HashMap<String, Object>();
        params.put("markdownFile", filename);
        params.put("editMode", editMode);
        params.put("title", title);
        params.put("urlQueryParams", queryParams);
        params.put("directoryName", markdownFile.getParent().equals(markdownRoot) ? "" :
                markdownRoot.relativize(markdownFile.getParent()).toString().replace("\\", "/"));
        templateEngine.render("render/markdown.jte", params, output);

        return new ContentWithType(output.toString(), "text/html");
    }

    private static String constructMarkdownTitle(String filename, Path markdownRoot) {
        String fileNameWithoutExtension = filename.substring(filename.lastIndexOf('/') == -1 ? 0 : filename.lastIndexOf('/') + 1);
        fileNameWithoutExtension = fileNameWithoutExtension.replaceAll(".md$", "");
        Path fullPath = markdownRoot.resolve(filename);
        String title = fileNameWithoutExtension;

        if (fileNameWithoutExtension.startsWith("_")) {
            title = fullPath.getParent().getFileName().toString() + " > " + fileNameWithoutExtension;
        }

        return title;
    }

    public static String escapeHtml(String html) {
        return html.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    @Data
    @AllArgsConstructor
    public static class SaveResult {
        private String newFilename;
        private boolean conflict;
        private String timestampOfFileInEditor;
    }

    @PostMapping("/saveMarkdown")
    public @ResponseBody ResponseEntity<SaveResult> saveMarkdown(
            @RequestParam(name = "filename", required = false) String filename,
            @RequestParam(name = "timestampOfFileInEditor") String timestampOfFileInEditor,
            @RequestBody String content) {
        try {
            var decodedFilename = URLDecoder.decode(filename, StandardCharsets.UTF_8);
            Path filePath = Paths.get(configService.getDocsDirectory(), decodedFilename);

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime dateTime = LocalDateTime.parse(timestampOfFileInEditor, formatter);

            long fileModifiedInstant = Files.getLastModifiedTime(filePath).toInstant().getEpochSecond();
            LocalDateTime timestampOfFileOnDisk = LocalDateTime.ofInstant(Instant.ofEpochSecond(fileModifiedInstant),
                    ZoneId.systemDefault());
            // try to save file under a new filename
            int num = 1;
            var newFilename = filename + "_conflict";
            while (Files.exists(filePath.getParent().resolve(newFilename))) {
                newFilename = filename + "_conflict_" + num++;
            }

            if (timestampOfFileOnDisk.isAfter(dateTime)) {
                Files.write(filePath.getParent().resolve(newFilename), content.getBytes());
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new SaveResult(newFilename, true, timestampOfFileInEditor));
            }
            Files.write(filePath, content.getBytes());
            long updatedFileInstant = Files.getLastModifiedTime(filePath).toInstant().getEpochSecond();
            LocalDateTime timestampOfUpdatedFile = LocalDateTime.ofInstant(Instant.ofEpochSecond(updatedFileInstant),
                    ZoneId.systemDefault());
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(new SaveResult(filename, false, timestampOfUpdatedFile.format(formatter)));
        } catch (Exception e) {
            log.error("Error saving markdown file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new SaveResult(filename, false, timestampOfFileInEditor)
            );
        }
    }
}