package uk.anbu.devnotes.module;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.plantuml.SourceStringReader;
import org.apache.commons.io.FileUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import uk.anbu.devnotes.service.ConfigService;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlantumlRenderer {
    private final ConfigService configService;

    public Optional<Resource> renderPlantuml(String path, String filename) {
        Path markdownRoot = Paths.get(configService.getDocsDirectory());

        File plantumlFile = markdownRoot.resolve(path == null ? "" : path)
                .resolve(filename)
                .toFile();
        try {
            String content = FileUtils.readFileToString(plantumlFile, StandardCharsets.UTF_8);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            SourceStringReader reader = new SourceStringReader(content);
            reader.outputImage(png);
            return Optional.of(new ByteArrayResource(png.toByteArray()));
        } catch (Exception e) {
            log.error("Failed to render plantuml file: {}", filename, e);
            return Optional.empty();
        }
    }
}
