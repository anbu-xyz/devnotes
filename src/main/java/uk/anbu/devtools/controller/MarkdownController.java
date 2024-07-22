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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RestController
@Slf4j
public class MarkdownController {

    @Value("${markdown.directory}")
    private String markdownDirectory;

    @GetMapping("/convert/{filename}")
    public Object convertMarkdownToHtml(@PathVariable String filename) {
        log.info("Fetching file: {}", filename);
        var requestedResource = Paths.get(markdownDirectory, filename).toFile();
        if (!requestedResource.exists()) {
            // try to decode file name
            byte[] decodedBytes = Base64.getDecoder().decode(filename);
            var newFilename = new String(decodedBytes);
            if (Paths.get(markdownDirectory, newFilename).toFile().exists()) {
                filename = newFilename;
            }
        }
        if ("md".equalsIgnoreCase(getFileExtension(filename))) {
            log.info("Rendering markdown: {}", filename);
            return renderMarkdown(filename);
        } else {
            log.info("Rending image: {}", filename);
            return getImage(filename);
        }
    }

    public String renderMarkdown(String fileName) {
        String markdownFilePath = Paths.get(markdownDirectory, fileName).toString();
        try {
            String markdownContent = new String(Files.readAllBytes(Paths.get(markdownFilePath)));
            return wrapHtmlWithCss(convertMarkdown(markdownContent), fileName);
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
        // Process the document and encode links
        processDocument(document);
        HtmlRenderer renderer = HtmlRenderer.builder()
                .extensions(extensions)
                .attributeProviderFactory(context -> new ImageAttributeProvider())
                .build();
        return renderer.render(document);
    }

    private void processDocument(Node node) {
        // Traverse the node tree
        if (node instanceof Link link) {
            String destination = link.getDestination();
            if (destination.startsWith("http:") || destination.startsWith("https:")) {
                log.trace("Link destination {} starts with http", destination);
            } else {
                String encodedUrl = Base64.getEncoder().encodeToString(link.getDestination().getBytes());
                link.setDestination(encodedUrl);
            }
        } else if (node instanceof Image image) {
            String encodedUrl = Base64.getEncoder().encodeToString(image.getDestination().getBytes());
            image.setDestination(encodedUrl);
        }
        // Recursively process child nodes
        if (node.getNext() != null) {
            processDocument(node.getNext());
        } else if (node.getFirstChild() != null) {
            processDocument(node.getFirstChild());
        }
    }

    public static class ImageAttributeProvider implements AttributeProvider {
        @Override
        public void setAttributes(Node node, String tagName, Map<String, String> attributes) {
            log.info("Rendering type: {}", node);
            if (node instanceof Image) {
                attributes.put("class", "border");
            }
        }
    }

    private ResponseEntity<Resource> getImage(String filename) {
        try {
            File imageFile = Paths.get(markdownDirectory, filename).toFile();
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

    private String wrapHtmlWithCss(String htmlContent, String filename) {
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