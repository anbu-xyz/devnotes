package uk.anbu.devnotes.controller;

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
import uk.anbu.devnotes.markdown.code.DataBlockTranslator;
import uk.anbu.devnotes.module.GroovyRenderer;
import uk.anbu.devnotes.service.ConfigService;
import uk.anbu.devnotes.types.MarkdownFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

import static uk.anbu.devnotes.util.FileBasedCache.generateCacheFileName;
import static uk.anbu.devnotes.util.FileBasedCache.generateHash;

@RestController
@RequestMapping("/groovy")
@RequiredArgsConstructor
@Slf4j
public class GroovyRefreshController {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ConfigService configService;

    @PostMapping(value = "/fragment", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> renderFragment(@RequestBody Map<String, Object> body) {
        var markdownFileStr = extractRequiredParam(body, "markdownFile");
        var groovyId = extractRequiredParam(body, "groovyId");
        if (markdownFileStr.isEmpty() || groovyId.isEmpty()) {
            return ResponseEntity.badRequest().body("missing markdownFile or groovyId in request body");
        }
        try {
            var mdPathOpt = resolveMdPath(markdownFileStr.get());
            if (mdPathOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("markdown file not found: " + markdownFileStr.get());
            }
            var content = Files.readString(mdPathOpt.get());
            var document = Parser.builder().build().parse(content);
            var mdFile = new MarkdownFile(Path.of(configService.getDocsDirectory()), markdownFileStr.get());
            return findAndRender(document, mdFile, groovyId.get())
                    .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body("groovyId not found in file"));
        } catch (Exception e) {
            log.error("Error refreshing groovy block", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error: " + e.getMessage());
        }
    }

    @PostMapping(value = "/edit", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> editBlock(@RequestBody Map<String, Object> body) {
        var markdownFileStr = extractRequiredParam(body, "markdownFile");
        var blockId = extractRequiredParam(body, "blockId");
        var newContent = Optional.ofNullable(body.get("newContent")).map(Object::toString);

        if (markdownFileStr.isEmpty() || blockId.isEmpty() || newContent.isEmpty()) {
            return ResponseEntity.badRequest().body("missing markdownFile, blockId, or newContent");
        }

        try {
            var mdPathOpt = resolveMdPath(markdownFileStr.get());
            if (mdPathOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("markdown file not found: " + markdownFileStr.get());
            }
            Path mdPath = mdPathOpt.get();

            // Conflict check
            var fileLastModifiedStr = extractRequiredParam(body, "fileLastModified");
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
            var document = Parser.builder().build().parse(rawMarkdown);
            var mdFile = new MarkdownFile(Path.of(configService.getDocsDirectory()), markdownFileStr.get());

            // Find matching groovy-exec block by hash
            FencedCodeBlock matchedFcb = null;
            for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
                if (node instanceof FencedCodeBlock fcb && isGroovyBlock(fcb.getInfo())
                        && blockId.get().equals(generateHash(fcb.getLiteral()))) {
                    matchedFcb = fcb;
                    break;
                }
            }
            if (matchedFcb == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("blockId not found in file");
            }

            // Delete the OLD cache file (keyed on the old literal's hash)
            String oldCacheFileName = generateCacheFileName(mdFile, matchedFcb.getLiteral());
            Files.deleteIfExists(Path.of(oldCacheFileName));

            // Replace fence in raw markdown
            String oldFence = DataBlockTranslator.reconstructFence(matchedFcb);
            String safeNewContent = newContent.get().endsWith("\n") ? newContent.get() : newContent.get() + "\n";
            String indent = " ".repeat(matchedFcb.getFenceIndent());
            int fenceLen = matchedFcb.getOpeningFenceLength() != null ? matchedFcb.getOpeningFenceLength() : 3;
            String fenceMarker = matchedFcb.getFenceCharacter().repeat(fenceLen);
            String newFence = indent + fenceMarker + (matchedFcb.getInfo() != null ? matchedFcb.getInfo() : "") + "\n"
                    + safeNewContent + indent + fenceMarker + "\n";
            String updatedMarkdown = rawMarkdown.replace(oldFence, newFence);
            Files.writeString(mdPath, updatedMarkdown);

            // New last-modified time
            String newLastModified = Files.getLastModifiedTime(mdPath).toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDateTime().withNano(0).format(TS_FMT);

            // Re-render with the new content
            String newGroovyId = generateHash(safeNewContent);
            String newCacheFileName = generateCacheFileName(mdFile, safeNewContent);
            // Build a synthetic FencedCodeBlock representing the new content
            var newFcb = new FencedCodeBlock();
            newFcb.setInfo(matchedFcb.getInfo());
            newFcb.setFenceCharacter(matchedFcb.getFenceCharacter());
            newFcb.setOpeningFenceLength(matchedFcb.getOpeningFenceLength());
            newFcb.setFenceIndent(matchedFcb.getFenceIndent());
            newFcb.setLiteral(safeNewContent);

            var groovyRenderer = new GroovyRenderer(configService::getChromeDriverLocation);
            return groovyRenderer.renderResultFresh(newFcb, newCacheFileName, newGroovyId)
                    .map(node -> {
                        var html = node instanceof HtmlBlock hb ? hb.getLiteral() : node.toString();
                        return ResponseEntity.ok()
                                .contentType(MediaType.TEXT_HTML)
                                .header("X-File-Last-Modified", newLastModified)
                                .body(html);
                    })
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("failed to render groovy block"));

        } catch (Exception e) {
            log.error("Error editing groovy block", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error: " + e.getMessage());
        }
    }

    private Optional<String> extractRequiredParam(Map<String, Object> body, String key) {
        return Optional.ofNullable(body.get(key))
                .map(Object::toString)
                .filter(s -> !s.isBlank());
    }

    private Optional<Path> resolveMdPath(String markdownFileStr) {
        var mdPath = Paths.get(configService.getDocsDirectory()).resolve(markdownFileStr);
        if (!mdPath.isAbsolute()) {
            mdPath = Path.of("").toAbsolutePath().resolve(markdownFileStr).normalize();
        }
        if (Files.exists(mdPath) && Files.isRegularFile(mdPath)) {
            return Optional.of(mdPath);
        }
        return Optional.empty();
    }

    private Optional<ResponseEntity<String>> findAndRender(Node document, MarkdownFile mdFile, String groovyId) {
        for (var node = document.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof FencedCodeBlock fcb
                    && isGroovyBlock(fcb.getInfo())
                    && groovyId.equals(generateHash(fcb.getLiteral()))) {
                return Optional.of(renderMatchedBlock(fcb, mdFile, groovyId));
            }
        }
        return Optional.empty();
    }

    private static boolean isGroovyBlock(String info) {
        return "groovy-exec".equals(info);
    }

    private ResponseEntity<String> renderMatchedBlock(FencedCodeBlock fcb, MarkdownFile mdFile, String groovyId) {
        var groovyRenderer = new GroovyRenderer(configService::getChromeDriverLocation);
        var cacheFileName = generateCacheFileName(mdFile, fcb.getLiteral());
        return groovyRenderer.renderResultFresh(fcb, cacheFileName, groovyId)
                .map(node -> {
                    var html = node instanceof HtmlBlock hb ? hb.getLiteral() : node.toString();
                    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("failed to render groovy block"));
    }
}