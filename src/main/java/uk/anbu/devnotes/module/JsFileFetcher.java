package uk.anbu.devnotes.module;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;
import uk.anbu.devnotes.service.ConfigService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JsFileFetcher {
    private final ConfigService configService;

    public Optional<Resource> jsFile(String path, String filename) throws IOException {
        Path markdownRoot = Paths.get(configService.getDocsDirectory());

        File jsFile = markdownRoot.resolve(path == null ? "" : path)
                .resolve(filename.replaceFirst("^/", ""))
                .toFile();
        log.info("Javascript file {}", jsFile);
        if (!jsFile.exists() || !jsFile.isFile()) {
            return Optional.empty();
        }

        return Optional.of(new UrlResource(jsFile.toURI()));
    }
}
