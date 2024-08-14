package uk.anbu.devtools.controller;

import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devtools.service.ConfigService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@RestController
public class DirectoryListingController {

    private final ConfigService configService;

    private final TemplateEngine templateEngine;

    @PostMapping("/createSubdirectory")
    public ResponseEntity<String> createSubdirectory(@RequestParam String path, @RequestParam String name) {
        try {
            Path markdownRoot = Paths.get(configService.getDocsDirectory());
            Path newDirPath = markdownRoot.resolve(path).resolve(name);
            Files.createDirectories(newDirPath);
            return ResponseEntity.ok("Subdirectory created successfully");
        } catch (IOException e) {
            log.error("Error creating subdirectory", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating subdirectory");
        }
    }

    @PostMapping("/createMarkdown")
    public ResponseEntity<String> createMarkdown(@RequestParam String path, @RequestParam String name) {
        try {
            Path markdownRoot = Paths.get(configService.getDocsDirectory());
            Path newFilePath = markdownRoot.resolve(path).resolve(name);
            Files.createFile(newFilePath);
            return ResponseEntity.ok("Markdown file created successfully");
        } catch (IOException e) {
            log.error("Error creating markdown file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating markdown file");
        }
    }

    @PostMapping("/renameEntry")
    public ResponseEntity<String> renameEntry(@RequestParam String path, @RequestParam String oldName, @RequestParam String newName) {
        try {
            Path markdownRoot = Paths.get(configService.getDocsDirectory());
            Path oldPath = markdownRoot.resolve(path).resolve(oldName);
            Path newPath = markdownRoot.resolve(path).resolve(newName);
            Files.move(oldPath, newPath);
            return ResponseEntity.ok("Entry renamed successfully");
        } catch (IOException e) {
            log.error("Error renaming entry", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error renaming entry");
        }
    }

    @PostMapping("/deleteEntry")
    public ResponseEntity<String> deleteEntry(@RequestParam String path, @RequestParam String name) {
        try {
            Path markdownRoot = Paths.get(configService.getDocsDirectory());
            Path entryPath = markdownRoot.resolve(path).resolve(name);
            if (Files.isDirectory(entryPath)) {
                FileUtils.deleteDirectory(entryPath.toFile());
            } else {
                Files.delete(entryPath);
            }
            return ResponseEntity.ok("Entry deleted successfully");
        } catch (IOException e) {
            log.error("Error deleting entry", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting entry");
        }
    }

    @GetMapping("/renderDirectoryContents")
    public ResponseEntity<String> renderDirectoryContents(@RequestParam String directoryName) throws IOException {
        Path markdownRoot = Paths.get(configService.getDocsDirectory());
        Path filePath = markdownRoot.resolve(directoryName);

        try (var filesList = Files.list(filePath)) {
            var entries = filesList
                    .map(path -> new FileEntry(path.getFileName().toString(), Files.isDirectory(path)))
                    .sorted(Comparator.<FileEntry>comparingInt(e -> e.isDirectory() ? 0 : 1)
                            .thenComparing(f -> f.filename))
                    .toList();

            var model = Map.of(
                    "directoryName", directoryName,
                    "entries", entries
            );
            TemplateOutput output = new StringOutput();
            templateEngine.render("directory-listing.jte", model, output);
            return ResponseEntity.ok(output.toString());
        }
    }

    public record FileEntry(String filename, boolean isDirectory) {
    }
}