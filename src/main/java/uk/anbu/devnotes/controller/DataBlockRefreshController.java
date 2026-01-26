package uk.anbu.devnotes.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devnotes.markdown.code.DataBlockTranslator;
import uk.anbu.devnotes.markdown.code.datablock.YamlCodeblockConfig;
import uk.anbu.devnotes.service.ConfigService;
import uk.anbu.devnotes.markdown.code.datablock.ParameterRegistry;
import uk.anbu.devnotes.service.DatasourceConfigResolver;
import uk.anbu.devnotes.types.MarkdownFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@RestController
@RequestMapping("/datablock")
@Slf4j
public class DataBlockRefreshController {

    private final ConfigService configService;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public DataBlockRefreshController(ConfigService configService) {
        this.configService = configService;
    }

    @PostMapping(value = "/fragment", consumes = MediaType.APPLICATION_JSON_VALUE, 
            produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> renderFragment(@RequestBody Map<String, String> body) {
        String markdownFile = body.get("markdownFile");
        MarkdownFile mdFile = new MarkdownFile(Path.of(configService.getDocsDirectory()), markdownFile);
        String datablockId = body.get("datablockId");
        if (markdownFile == null || markdownFile.isBlank() || datablockId == null || datablockId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("missing markdownFile or datablockId");
        }

        try {
            Path markdownRoot = Paths.get(configService.getDocsDirectory());
            Path mdPath = markdownRoot.resolve(markdownFile);

            if (!mdPath.isAbsolute()) {
                mdPath = Path.of("").toAbsolutePath().resolve(markdownFile).normalize();
            }
            if (!Files.exists(mdPath) || !Files.isRegularFile(mdPath)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("markdown file not found: " + markdownFile);
            }

            String content = Files.readString(mdPath);
            Parser parser = Parser.builder().build();
            Node document = parser.parse(content);

            for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
                if (node instanceof FencedCodeBlock fcb) {
                    String info = fcb.getInfo();
                    if (info != null && info.trim().equals("data")) {
                        var textHtml = constructResponse(fcb, datablockId, mdFile);
                        if (textHtml != null) {
                            return textHtml;
                        }
                    }
                }
            }

            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("datablockId not found in file");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("error: " + e.getMessage());
        }
    }

    private ResponseEntity<String> constructResponse(FencedCodeBlock fcb,
                                                     String datablockId,
                                                     MarkdownFile mdFile) {
        String yaml = fcb.getLiteral();
        try {
            YamlCodeblockConfig config = yamlMapper.readValue(yaml, YamlCodeblockConfig.class);
            String checksum = config.checksum();
            if (datablockId.equals(checksum)) {
                // Found matching block; render fresh (bypass cache)
                var resolver = (DatasourceConfigResolver) configService::getDataSourceConfig;
                var registry = new ParameterRegistry();
                DataBlockTranslator translator = new DataBlockTranslator(resolver, configService, registry);
                var rendered = translator.renderDataBlockFreshFromYaml(yaml, mdFile);
                if (rendered.isPresent() && rendered.get() instanceof org.commonmark.node.HtmlBlock hb) {
                    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(hb.getLiteral());
                } else if (rendered.isPresent()) {
                    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(rendered.get().toString());
                } else {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("failed to render data block");
                }
            }
        } catch (Exception e) {
            log.error("Error processing datablock", e);
        }
        return null;
    }
}
