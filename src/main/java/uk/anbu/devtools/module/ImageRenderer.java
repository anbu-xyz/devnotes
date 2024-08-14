package uk.anbu.devtools.module;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;
import uk.anbu.devtools.service.ConfigService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImageRenderer {

    private final ConfigService configService;

    public Optional<Resource> image(String path, String filename) throws IOException {
        Path markdownRoot = Paths.get(configService.getDocsDirectory());
        File imageFile = markdownRoot.resolve(path == null ? "" : path).resolve(filename).toFile();
        log.info("Fetching image {}", imageFile);
        if (!imageFile.exists() || !imageFile.isFile()) {
            return Optional.empty();
        }

        return Optional.of(new UrlResource(imageFile.toURI()));
    }

}
