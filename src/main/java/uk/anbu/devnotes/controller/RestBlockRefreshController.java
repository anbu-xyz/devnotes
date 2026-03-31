package uk.anbu.devnotes.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devnotes.markdown.code.RestBlockTranslator;
import uk.anbu.devnotes.markdown.code.restblock.RestCodeblockConfig;
import uk.anbu.devnotes.service.ConfigService;
import uk.anbu.devnotes.types.MarkdownFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/restblock")
@RequiredArgsConstructor
@Slf4j
public class RestBlockRefreshController {

    private final ConfigService configService;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @PostMapping(value = "/fragment",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> renderFragment(@RequestBody Map<String, Object> body) {
        var markdownFileOpt = Optional.ofNullable(body.get("markdownFile"));
        var restblockIdOpt = Optional.ofNullable(body.get("restblockId"));

        if (markdownFileOpt.isEmpty() || markdownFileOpt.get().toString().isBlank()
                || restblockIdOpt.isEmpty() || restblockIdOpt.get().toString().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("missing markdownFile or restblockId in input");
        }

        var markdownFileStr = markdownFileOpt.get().toString();
        var restblockId = restblockIdOpt.get().toString();

        try {
            Path markdownRoot = Paths.get(configService.getDocsDirectory());
            Path mdPath = markdownRoot.resolve(markdownFileStr);
            if (!mdPath.isAbsolute()) {
                mdPath = Path.of("").toAbsolutePath().resolve(markdownFileStr).normalize();
            }
            if (!Files.exists(mdPath) || !Files.isRegularFile(mdPath)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("markdown file not found: " + markdownFileStr);
            }

            var mdFile = new MarkdownFile(markdownRoot, markdownFileStr);
            var content = Files.readString(mdPath);
            var parser = Parser.builder().build();
            var document = parser.parse(content);

            for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
                if (node instanceof FencedCodeBlock fcb) {
                    var info = fcb.getInfo();
                    if (info != null && info.trim().equals("rest")) {
                        var response = constructResponseOfRestblock(fcb, restblockId, mdFile);
                        if (response != null) {
                            return response;
                        }
                    }
                }
            }

            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("restblockId not found in file");
        } catch (Exception e) {
            log.error("Error refreshing rest block", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("error: " + e.getMessage());
        }
    }

    private ResponseEntity<String> constructResponseOfRestblock(FencedCodeBlock fcb,
                                                                String restblockId,
                                                                MarkdownFile mdFile) {
        var yaml = fcb.getLiteral();
        try {
            var config = yamlMapper.readValue(yaml, RestCodeblockConfig.class);
            var checksum = config.checksum();
            log.debug("Checking restblock checksum {} against requested {}", checksum, restblockId);
            if (restblockId.equals(checksum)) {
                // Delete cache file so translator re-executes fresh
                var translator = new RestBlockTranslator();
                var cacheFile = translator.cacheFilePath(mdFile, checksum);
                Files.deleteIfExists(cacheFile);

                var rendered = translator.translate(yaml, mdFile);
                if (rendered.isPresent() && rendered.get() instanceof HtmlBlock hb) {
                    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(hb.getLiteral());
                } else if (rendered.isPresent()) {
                    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                            .body(rendered.get().toString());
                } else {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("failed to render rest block");
                }
            }
        } catch (Exception e) {
            log.error("Error processing restblock", e);
        }
        return null;
    }
}