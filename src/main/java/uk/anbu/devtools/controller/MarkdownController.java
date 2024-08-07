package uk.anbu.devtools.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devtools.module.ImageRenderer;
import uk.anbu.devtools.module.MarkdownRenderer;
import uk.anbu.devtools.util.FileUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@RestController
public class MarkdownController {

    @Value("${markdown.directory}")
    private String markdownDirectory;

    private final MarkdownRenderer markdownRenderer;

    private final ImageRenderer imageRenderer;

    @GetMapping("/markdown")
    public Object markdown(@RequestParam(name = "filename", required = false) String filename,
                           @RequestParam(name = "image", required = false) String imageName) throws IOException {
        if (filename == null) {
            filename = "index.md";
        } else if (filename.startsWith("/")) {
            filename = filename.substring(1);
        }
        var content = fetchMarkdownContent(filename);
        if (content.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        } else {
            return content.get();
        }
    }

    public Optional<String> fetchMarkdownContent(String filename) throws IOException {
        Path markdownRoot = Paths.get(markdownDirectory);
        log.info("Fetching file: {}", filename);
        var markdownFile = markdownRoot.resolve(filename);
        if (markdownFile.toFile().exists()) {
            if ("md".equalsIgnoreCase(FileUtil.getFileExtension(filename))) {
                log.info("Rendering markdown: {}", markdownFile);
                String markdownContent = new String(Files.readAllBytes(markdownFile));
                return Optional.of(wrapHtmlWithCss(markdownRenderer.convertMarkdown(markdownContent), markdownFile));
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

    private String wrapHtmlWithCss(String htmlContent, Path filename) {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<link rel=\"stylesheet\" type=\"text/css\" href=\"/css/style.css\">" +
                "<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/themes/prism-tomorrow.min.css\">" +
                "<title>" + filename + "</title>" +
                "</head>" +
                "<body class=\"p-8\">" +
                htmlContent +
                "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/prism.min.js\"></script>" +
                "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/components/prism-python.min.js\"></script>" +
                "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/components/prism-javascript.min.js\"></script>" +
                "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/components/prism-java.min.js\"></script>" +
                "</body>" +
                "</html>";
    }
}