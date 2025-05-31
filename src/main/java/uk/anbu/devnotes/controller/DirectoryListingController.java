package uk.anbu.devnotes.controller;

import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uk.anbu.devnotes.service.ConfigService;
import uk.anbu.devnotes.util.FileUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@RestController
public class DirectoryListingController {

    private final ConfigService configService;

    private final TemplateEngine templateEngine;

    @GetMapping("/")
    public ResponseEntity<Object> index() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "/renderDirectoryContents?directoryName=.")
                .build();
    }

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
        if (directoryName.startsWith("/")) {
            // prevent directory traversal to root directory
            directoryName = directoryName.replaceAll("^/+", "");
        }
        directoryName = directoryName.replaceAll("\\\\", "/");
        directoryName = FileUtil.cleanDirectoryName(directoryName);
        Path markdownRoot = Paths.get(configService.getDocsDirectory());
        Path filePath = markdownRoot.resolve(directoryName);
        String parentDirectoryName = filePath.getParent().toString();
        // in the following line replaceFirst escapes the regex, so we need to unescape it
        parentDirectoryName= parentDirectoryName
                .replaceFirst(markdownRoot.toString().replaceAll("\\\\", "\\\\\\\\"), "")
                .replaceAll("\\\\", "/");

        parentDirectoryName = FileUtil.cleanDirectoryName(parentDirectoryName);

        // if the directory doesn't exist or is not a directory, return a 404 error
        if (!Files.exists(filePath) || !Files.isDirectory(filePath)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(String.format("<h1>Directory %s not found</h1>", directoryName));
        }
        try (var filesList = Files.list(filePath)) {
            var entries = filesList
                    .map(path -> new FileEntry(path.getFileName().toString(),
                            fileExtension(path.getFileName().toString()),
                            Files.isDirectory(path),
                            lastModifiedSince(path)))
                    .sorted(Comparator.<FileEntry>comparingInt(e -> e.isDirectory() ? 0 : 1)
                            .thenComparing(f -> f.filename))
                    .filter(e -> !e.filename.endsWith(".output"))
                    .toList();

            String title;
            Path directoryPath = markdownRoot.resolve(directoryName);
            if (directoryName.equals(".")) {
                title = "Home";
            } else {
                title = directoryPath.getFileName().toString() + "/";
            }

            var model = Map.of(
                    "directoryName", directoryName,
                    "parentDirectoryName", parentDirectoryName,
                    "title", title,
                    "entries", entries,
                    "fileSystemPath", directoryPath.toAbsolutePath().toString()
            );
            TemplateOutput output = new StringOutput();
            templateEngine.render("directory-listing.jte", model, output);
            return ResponseEntity.ok(output.toString());
        }
    }

    @SneakyThrows
    private static String lastModifiedSince(Path path) {
        var modifiedTime = Files.getLastModifiedTime(path).toInstant();
        var now = Instant.now();
        var duration = Duration.between(modifiedTime, now);
        return toHumanReadable(duration);
    }

    public static String toHumanReadable(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;

        if (days >= 365 * 2) {
            long years = days / 365;
            return years + " years ago";
        }

        if (days > 365) {
            long months = days / 30;
            return months + " months ago";
        }

        if (days >= 28 && days < 60) {
            return "a month ago";
        }

        if (days >= 60) {
            long months = days / 30;
            return months + " months ago";
        }

        if (days >= 14) {
            long weeks = days / 7;
            return weeks + " weeks ago";
        }

        if (days >= 7) {
            return "a week ago";
        }

        if (days > 0) {
            if (days == 1) {
                return "yesterday";
            }
            return days + " days ago";
        }

        if (hours > 0) {
            if (hours == 1) {
                return "an hour ago";
            }
            return hours + " hours ago";
        }

        if (minutes > 45) {
            return "about an hour ago";
        }

        if (minutes > 30) {
            return "about half an hour ago";
        }

        if (minutes > 0) {
            if (minutes == 1) {
                return "1 minute ago";
            }
            return minutes + " minutes ago";
        }

        return "just now";
    }

    private String fileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }

    public record FileEntry(String filename, String fileType, boolean isDirectory, String lastModified) {
    }

    @PostMapping("/uploadFile")
    public ResponseEntity<String> uploadFile(@RequestParam MultipartFile file, @RequestParam String path) {
        try {
            Path markdownRoot = Paths.get(configService.getDocsDirectory());
            Path uploadPath = markdownRoot.resolve(path);
            Path filePath = uploadPath.resolve(file.getOriginalFilename());

            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            return ResponseEntity.ok("File uploaded successfully");
        } catch (IOException e) {
            log.error("Error uploading file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error uploading file");
        }
    }
}