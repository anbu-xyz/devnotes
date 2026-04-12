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
import uk.anbu.devnotes.markdown.code.ParameterBlockTranslator;
import uk.anbu.devnotes.markdown.code.datablock.ParameterRegistry;
import uk.anbu.devnotes.markdown.code.datablock.YamlCodeblockConfig;
import uk.anbu.devnotes.service.ConfigService;
import uk.anbu.devnotes.service.DatasourceConfigResolver;
import uk.anbu.devnotes.service.EncryptionService;
import uk.anbu.devnotes.service.ExchangeRateService;
import uk.anbu.devnotes.types.MarkdownFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/datablock")
@Slf4j
public class DataBlockRefreshController {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
        var checksumParams = normalizeFragmentChecksumParams(params);

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
                                checksumParams);
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

    private Map<String, Object> normalizeFragmentChecksumParams(Map<String, Object> params) {
        var normalized = new LinkedHashMap<String, Object>();
        if (params != null && !params.isEmpty()) {
            normalized.putAll(params);
        }
        // Internal route param must not influence data-block id checksums.
        normalized.remove("filename");
        if (!normalized.containsKey("__exchangeRatesVersion")) {
            var version = exchangeRateService != null ? exchangeRateService.getVersion() : 0L;
            normalized.put("__exchangeRatesVersion", version);
        }
        return normalized;
    }

    @PostMapping(value = "/edit", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> editBlock(@RequestBody Map<String, Object> body) {
        var markdownFileStr = Optional.ofNullable(body.get("markdownFile")).map(Object::toString).filter(s -> !s.isBlank());
        var blockId = Optional.ofNullable(body.get("blockId")).map(Object::toString).filter(s -> !s.isBlank());
        var newContent = Optional.ofNullable(body.get("newContent")).map(Object::toString);

        if (markdownFileStr.isEmpty() || blockId.isEmpty() || newContent.isEmpty()) {
            return ResponseEntity.badRequest().body("missing markdownFile, blockId, or newContent");
        }

        try {
            Path mdPath = Paths.get(configService.getDocsDirectory()).resolve(markdownFileStr.get());
            if (!mdPath.isAbsolute()) {
                mdPath = Path.of("").toAbsolutePath().resolve(markdownFileStr.get()).normalize();
            }
            if (!Files.exists(mdPath) || !Files.isRegularFile(mdPath)) {
                return ResponseEntity.badRequest().body("markdown file not found: " + markdownFileStr.get());
            }

            // Conflict check
            var fileLastModifiedStr = Optional.ofNullable(body.get("fileLastModified")).map(Object::toString).filter(s -> !s.isBlank());
            if (fileLastModifiedStr.isPresent()) {
                LocalDateTime clientTime = LocalDateTime.parse(fileLastModifiedStr.get(), TS_FMT);
                FileTime diskTime = Files.getLastModifiedTime(mdPath);
                LocalDateTime diskDateTime = diskTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().withNano(0);
                if (diskDateTime.isAfter(clientTime)) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body("File was modified after the page was loaded");
                }
            }

            String rawMarkdown = Files.readString(mdPath);
            var parser = Parser.builder().build();
            var document = parser.parse(rawMarkdown);

            var mdFile = new MarkdownFile(Path.of(configService.getDocsDirectory()), markdownFileStr.get());

            // Walk fence blocks in order, accumulating parameter blocks before the target data block
            var registry = new ParameterRegistry();
            FencedCodeBlock matchedFcb = null;
            Map<String, Object> sharedParams = null;

            var fencedBlocks = collectFencedCodeBlocks(document);
            for (var fcb : fencedBlocks) {
                String info = fcb.getInfo() == null ? "" : fcb.getInfo().trim();
                if ("parameter".equals(info)) {
                    new ParameterBlockTranslator(registry).renderParameterBlock(fcb.getLiteral());
                } else if ("data".equals(info)) {
                    // Build shared params at this point (including exchange-rate version)
                    var params = new LinkedHashMap<>(registry.getAll());
                    params.put("__exchangeRatesVersion", exchangeRateService.getVersion());
                    try {
                        YamlCodeblockConfig config = yamlMapper.readValue(fcb.getLiteral(), YamlCodeblockConfig.class);

                        var checksums = candidateChecksums(config, params);
                        if (checksums.contains(blockId.get())) {
                            matchedFcb = fcb;
                            sharedParams = params;
                            break;
                        }
                    } catch (Exception ex) {
                        log.debug("Could not parse data block YAML during edit search", ex);
                    }
                }
            }

            if (matchedFcb == null) {
                log.warn("Data block id {} not found in {}", blockId.get(), markdownFileStr.get());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("blockId not found in file");
            }

            // Delete the old cache file
            DataBlockTranslator.deleteCache(mdFile, blockId.get());

            // Replace the fence block in the raw markdown
            String oldFence = DataBlockTranslator.reconstructFence(matchedFcb);
            String safeNewContent = newContent.get().endsWith("\n") ? newContent.get() : newContent.get() + "\n";
            String indent = " ".repeat(matchedFcb.getFenceIndent());
            int fenceLen = matchedFcb.getOpeningFenceLength() != null ? matchedFcb.getOpeningFenceLength() : 3;
            String fenceMarker = matchedFcb.getFenceCharacter().repeat(fenceLen);
            String newFence = indent + fenceMarker + (matchedFcb.getInfo() != null ? matchedFcb.getInfo() : "") + "\n"
                    + safeNewContent + indent + fenceMarker + "\n";
            String updatedMarkdown = rawMarkdown.replace(oldFence, newFence);
            Files.writeString(mdPath, updatedMarkdown);

            // Read new last-modified time
            String newLastModified = Files.getLastModifiedTime(mdPath).toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDateTime().withNano(0).format(TS_FMT);

            // Re-render with re-derived shared params
            var resolver = (DatasourceConfigResolver) configService::getDataSourceConfig;
            var freshRegistry = new ParameterRegistry();
            var translator = new DataBlockTranslator(resolver, configService, freshRegistry, exchangeRateService);
            var rendered = translator.renderDataBlockFreshFromYaml(safeNewContent, mdFile, sharedParams);

            if (rendered.isPresent() && rendered.get() instanceof org.commonmark.node.HtmlBlock hb) {
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .header("X-File-Last-Modified", newLastModified)
                        .body(hb.getLiteral());
            } else if (rendered.isPresent()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .header("X-File-Last-Modified", newLastModified)
                        .body(rendered.get().toString());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("failed to render data block");
            }

        } catch (Exception e) {
            log.error("Error editing data block", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error: " + e.getMessage());
        }
    }

    private static List<FencedCodeBlock> collectFencedCodeBlocks(Node root) {
        var result = new ArrayList<FencedCodeBlock>();
        collectFencedCodeBlocks(root, result);
        return result;
    }

    private static void collectFencedCodeBlocks(Node node, List<FencedCodeBlock> out) {
        for (var current = node.getFirstChild(); current != null; current = current.getNext()) {
            if (current instanceof FencedCodeBlock fcb) {
                out.add(fcb);
            }
            collectFencedCodeBlocks(current, out);
        }
    }

    private static Set<String> candidateChecksums(YamlCodeblockConfig config,
                                                   Map<String, Object> sharedParams) {
        var checksums = new HashSet<String>();

        checksums.add(config.checksum(sharedParams));

        // Backward compatibility: old pages may carry ids that predate exchange-rate cache key injection.
        var withoutVersion = new LinkedHashMap<>(sharedParams);
        withoutVersion.remove("__exchangeRatesVersion");
        checksums.add(config.checksum(withoutVersion));


        return checksums;
    }
}
