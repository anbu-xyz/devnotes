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
import uk.anbu.devnotes.service.EncryptionService;
import uk.anbu.devnotes.service.ExchangeRateService;
import uk.anbu.devnotes.types.MarkdownFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/datablock")
@Slf4j
public class DataBlockRefreshController {

    private final ConfigService configService;
    private final ExchangeRateService exchangeRateService;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public DataBlockRefreshController(ConfigService configService, ExchangeRateService exchangeRateService) {
        this.configService = configService;
        this.exchangeRateService = exchangeRateService;
    }

    @PostMapping(value = "/fragment", consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> renderFragment(@RequestBody Map<String, Object> body) {
        var markdownFile = Optional.ofNullable(body.get("markdownFile"));
        var datablockId = Optional.ofNullable(body.get("datablockId"));
        if (markdownFile.isEmpty() || markdownFile.get().toString().isBlank()
            || datablockId.isEmpty() || datablockId.get().toString().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("missing markdownFile or datablockId in input");
        }
        MarkdownFile mdFile = new MarkdownFile(Path.of(configService.getDocsDirectory()),
            markdownFile.get().toString());
        Object rawParams = body.get("params");
        final Map<String, Object> params;
        if (rawParams == null) {
            params = Map.of();
        } else if (rawParams instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> castParams = (Map<String, Object>) rawParams;
            params = castParams;
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("'params' field must be an object/map");
        }

        try {
            Path markdownRoot = Paths.get(configService.getDocsDirectory());
            Path mdPath = markdownRoot.resolve(markdownFile.get().toString());

            if (!mdPath.isAbsolute()) {
                mdPath =
                    Path.of("").toAbsolutePath().resolve(markdownFile.get().toString()).normalize();
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
                        var textHtml =
                            constructResponseOfDatablock(fcb, datablockId.get().toString(), mdFile,
                                params);
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

    private ResponseEntity<String> constructResponseOfDatablock(FencedCodeBlock fcb,
                                                                String datablockId,
                                                                MarkdownFile mdFile,
                                                                Map<String, Object> params) {
        String yaml = fcb.getLiteral();
        try {
            YamlCodeblockConfig config = yamlMapper.readValue(yaml, YamlCodeblockConfig.class);
            String checksum = config.checksum(params);
            log.debug("Checking datablock with checksum {} against requested {}", checksum,
                datablockId);
            if (datablockId.equals(checksum)) {
                // Guard: encrypted password but no key loaded
                String sourceName = config.getSource();
                if (sourceName != null) {
                    String dsName = sourceName.contains("/")
                        ? sourceName.substring(sourceName.lastIndexOf('/') + 1) : sourceName;
                    ConfigService.DataSourceConfig ds = configService.getDataSourceConfig(dsName);
                    if (ds != null && EncryptionService.isEncrypted(ds.password())
                        && !configService.isEncryptionKeySet()) {
                        String warningHtml =
                            "<div class=\"enc-key-needed\"><i class=\"fas fa-lock\"></i>"
                                + " Datasource '" + dsName + "' has an encrypted password. "
                                +
                                "<a href=\"/config/encryption-key\">Enter encryption key</a></div>";
                        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                            .body(warningHtml);
                    }
                }
                // Found matching block; render fresh (bypass cache)
                var resolver = (DatasourceConfigResolver) configService::getDataSourceConfig;
                var registry = new ParameterRegistry();
                DataBlockTranslator translator =
                    new DataBlockTranslator(resolver, configService, registry, exchangeRateService);
                var rendered = translator.renderDataBlockFreshFromYaml(yaml, mdFile, params);
                if (rendered.isPresent() &&
                    rendered.get() instanceof org.commonmark.node.HtmlBlock hb) {
                    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                        .body(hb.getLiteral());
                } else if (rendered.isPresent()) {
                    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                        .body(rendered.get().toString());
                } else {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("failed to render data block");
                }
            }
        } catch (Exception e) {
            log.error("Error processing datablock", e);
        }
        return null;
    }
}
