package uk.anbu.devtools.controller;

import lombok.extern.slf4j.Slf4j;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.AttributeProvider;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RestController
@Slf4j
public class MarkdownController {

    @Value("${markdown.directory}")
    private String markdownDirectory;

    @GetMapping("/markdown")
    public Object markdown(@RequestParam(name = "filename", required = false) String filename,
                   @RequestParam(name = "image", required = false) String imageName) throws IOException {
        Path markdownRoot = Paths.get(markdownDirectory);
        if (filename == null) {
            filename = "index.md";
        } else if (filename.startsWith("/")) {
            filename = filename.substring(1);
        }
        log.info("Fetching file: {}", filename);
        var requestedResource = markdownRoot.resolve(filename);
        if (requestedResource.toFile().exists()) {
            if ("md".equalsIgnoreCase(getFileExtension(filename))) {
                log.info("Rendering markdown: {}", requestedResource);
                return renderMarkdown(requestedResource);
            } else {
                return Files.readString(markdownRoot.resolve(filename), StandardCharsets.UTF_8);
            }
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    public String renderMarkdown(Path markdownFile) {
        try {
            String markdownContent = new String(Files.readAllBytes(markdownFile));
            return wrapHtmlWithCss(convertMarkdown(markdownContent), markdownFile);
        } catch (IOException e) {
            return "Error reading the markdown file: " + e.getMessage();
        }
    }

    private String convertMarkdown(String markdown) {
        List<Extension> extensions = List.of(TablesExtension.create());
        Parser parser = Parser.builder()
                .extensions(extensions)
                .build();
        Node document = parser.parse(markdown);
        // Process the document and encode linksk
        processDocument(document);
        HtmlRenderer renderer = HtmlRenderer.builder()
                .extensions(extensions)
                .attributeProviderFactory(context -> new ImageAttributeProvider())
                .build();
        return renderer.render(document);
    }

    private void processDocument(Node node) {
        // Traverse the node tree
        log.info("Rendering type: {}", node);
        if (node instanceof Link link) {
            String destination = link.getDestination();
            if (destination.startsWith("http:") || destination.startsWith("https:")) {
                log.trace("Link destination {} starts with http", destination);
            } else {
                link.setDestination("?filename=" + URLEncoder.encode(link.getDestination(), StandardCharsets.UTF_8));
            }
        } else if (node instanceof Image image) {
            image.setDestination("/image?filename=" + URLEncoder.encode(image.getDestination(), StandardCharsets.UTF_8));
        }
        // Recursively process child nodes
        // process siblings first
        if (node.getNext() != null) {
            processDocument(node.getNext());
        }
        // Process any children now
        if (node.getFirstChild() != null) {
            processDocument(node.getFirstChild());
        }
    }

    public static class ImageAttributeProvider implements AttributeProvider {
        @Override
        public void setAttributes(Node node, String tagName, Map<String, String> attributes) {
//            log.info("Rendering type: {}", node);
            if (node instanceof Image) {
                attributes.put("class", "border");
            }
        }
    }

    @GetMapping("/image")
    public ResponseEntity<Resource> image(@RequestParam(name = "path", required = false) String path,
                           @RequestParam(name = "filename") String filename) throws IOException {
        try {
            File imageFile = Paths.get(markdownDirectory, path == null? "": path, filename).toFile();
            log.info("Fetching image {}", imageFile);
            if (!imageFile.exists() || !imageFile.isFile()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            Resource resource = new UrlResource(imageFile.toURI());
            String contentType = "image/" + getFileExtension(filename).toLowerCase();

            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < filename.length() - 1) {
            return filename.substring(dotIndex + 1);
        }
        return "";

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