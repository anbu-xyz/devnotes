package uk.anbu.devnotes.markdown.link;

import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.Link;
import org.commonmark.node.Node;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
public class LocalLinkTransformer {
    public static void transform(Node current) {
        if (current instanceof Link link) {
            String destination = link.getDestination();

            if (destination.startsWith("http:") || destination.startsWith("https:")) {
                log.trace("Link destination {} starts with http", destination);
            } else {
                link.setDestination("?filename=" + URLEncoder.encode(link.getDestination(), StandardCharsets.UTF_8));
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
