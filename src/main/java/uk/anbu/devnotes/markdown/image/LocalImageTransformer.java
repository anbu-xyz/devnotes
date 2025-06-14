package uk.anbu.devnotes.markdown.image;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.Image;
import org.commonmark.node.Node;
import uk.anbu.devnotes.types.MarkdownFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Builder
public class LocalImageTransformer {

    private final MarkdownFile markdownFile;

    public void transform(Node current) {
        if (current instanceof Image image) {
            String fileLocation = markdownFile.fileName()
                    .replaceAll("\\\\", "/") // Windows
                    .replaceFirst("/[^/]+$", ""); // Remove filename
            if (((Image) current).getDestination().startsWith("/plantumlContent?")) {
                // do nothing - this is to allow the plantuml renderer to render the image
            } else if (fileLocation.isEmpty() || fileLocation.equals(markdownFile.fileName())) { // If the file is in the root directory
                image.setDestination("/image?filename=" + URLEncoder.encode(image.getDestination(), StandardCharsets.UTF_8));
            } else if (image.getDestination().startsWith("/")) {
                image.setDestination("/image?filename=" + URLEncoder.encode(image.getDestination(), StandardCharsets.UTF_8));
            } else {
                image.setDestination("/image?filename=" + fileLocation + "/" + URLEncoder.encode(image.getDestination(), StandardCharsets.UTF_8));
            }
        }
        if (current.getNext() != null) {
            transform(current.getNext());
        }
        if (current.getFirstChild() != null) {
            transform(current.getFirstChild());
        }
    }
}
