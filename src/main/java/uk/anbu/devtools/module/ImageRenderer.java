package uk.anbu.devtools.module;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Optional;

@Slf4j
@Component
public class ImageRenderer {

    @Value("${markdown.directory}")
    private String markdownDirectory;

    public Optional<Resource> image(String path, String filename) throws IOException {
        File imageFile = Paths.get(markdownDirectory, path == null ? "" : path, filename).toFile();
        log.info("Fetching image {}", imageFile);
        if (!imageFile.exists() || !imageFile.isFile()) {
            return Optional.empty();
        }

        return Optional.of(new UrlResource(imageFile.toURI()));
    }

}
