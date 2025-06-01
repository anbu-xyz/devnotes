package uk.anbu.devnotes.module;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import uk.anbu.devnotes.types.ImageFile;

import java.io.IOException;
import java.util.Optional;

@Slf4j
public class ImageRenderer {

    public Optional<Resource> image(ImageFile imageFile) throws IOException {
        var file = imageFile.fullPath().toFile();
        log.info("Fetching image {}", file);
        if (!file.exists() || !file.isFile()) {
            return Optional.empty();
        }

        return Optional.of(new UrlResource(file.toURI()));
    }

}
