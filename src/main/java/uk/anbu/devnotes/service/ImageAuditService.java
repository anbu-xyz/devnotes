package uk.anbu.devnotes.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Image;
import org.commonmark.parser.Parser;
import org.springframework.stereotype.Service;
import uk.anbu.devnotes.controller.ImageController;
import uk.anbu.devnotes.util.FileUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageAuditService {

    private final ConfigService configService;

    public record BrokenImageLink(String markdownFile, String imageLink) {}

    public record ImageAuditResult(List<String> orphanedImages, List<BrokenImageLink> brokenLinks) {}

    public ImageAuditResult audit() throws IOException {
        Path docsDir = Path.of(configService.getDocsDirectory()).toAbsolutePath().normalize();

        // 1. Collect all image files on disk (normalised paths relative to docsDir)
        Set<Path> imageFilesOnDisk = new HashSet<>();
        try (var stream = Files.walk(docsDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> ImageController.isImage(
                            FileUtil.getFileExtension(p.getFileName().toString()).toLowerCase()))
                    .forEach(p -> imageFilesOnDisk.add(docsDir.relativize(p.normalize())));
        }

        // 2. Walk all markdown files and collect referenced / broken image links
        Parser parser = Parser.builder().build();
        Set<Path> referencedImages = new HashSet<>();
        List<BrokenImageLink> brokenLinks = new ArrayList<>();

        List<Path> mdFiles;
        try (var stream = Files.walk(docsDir)) {
            mdFiles = stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".md"))
                    .toList();
        }

        for (Path mdFile : mdFiles) {
            try {
                String content = Files.readString(mdFile);
                var document = parser.parse(content);
                Path mdDir = mdFile.getParent();
                String relMd = docsDir.relativize(mdFile).toString().replace('\\', '/');

                document.accept(new AbstractVisitor() {
                    @Override
                    public void visit(Image image) {
                        String dest = image.getDestination();
                        if (dest == null || dest.isBlank()) {
                            super.visit(image);
                            return;
                        }
                        // Skip external URLs and plantuml synthetic links
                        if (dest.startsWith("http://") || dest.startsWith("https://")
                                || dest.startsWith("/plantumlContent?")) {
                            super.visit(image);
                            return;
                        }

                        Path imagePath;
                        if (dest.startsWith("/")) {
                            // absolute path rooted at docsDir
                            imagePath = docsDir.resolve(dest.substring(1)).normalize();
                        } else {
                            // relative to the markdown file's directory
                            imagePath = mdDir.resolve(dest).normalize();
                        }

                        if (Files.exists(imagePath)) {
                            referencedImages.add(docsDir.relativize(imagePath));
                        } else {
                            brokenLinks.add(new BrokenImageLink(relMd, dest));
                        }

                        super.visit(image);
                    }
                });
            } catch (Exception e) {
                log.warn("Error processing markdown file for image audit: {}", mdFile, e);
            }
        }

        // 3. Orphaned = on disk but not referenced by any markdown
        Set<Path> orphaned = new HashSet<>(imageFilesOnDisk);
        orphaned.removeAll(referencedImages);

        List<String> orphanedList = orphaned.stream()
                .map(p -> p.toString().replace('\\', '/'))
                .sorted()
                .toList();

        brokenLinks.sort(java.util.Comparator
                .comparing(BrokenImageLink::markdownFile)
                .thenComparing(BrokenImageLink::imageLink));

        return new ImageAuditResult(orphanedList, brokenLinks);
    }

    /**
     * Deletes an orphaned image file identified by its path relative to the docs directory.
     * Rejects paths that attempt to escape the docs directory via {@code ..} segments.
     *
     * @param relativePath path relative to the docs directory, using {@code /} separators
     * @throws IllegalArgumentException if the path is unsafe or not an image
     * @throws IOException              if the file cannot be deleted
     */
    public void deleteOrphanedImage(String relativePath) throws IOException {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Image path must not be blank");
        }

        Path docsDir = Path.of(configService.getDocsDirectory()).toAbsolutePath().normalize();
        Path target = docsDir.resolve(relativePath).normalize();

        // Path-traversal guard
        if (!target.startsWith(docsDir)) {
            throw new IllegalArgumentException("Refusing to delete file outside docs directory");
        }

        String ext = uk.anbu.devnotes.util.FileUtil.getFileExtension(target.getFileName().toString()).toLowerCase();
        if (!ImageController.isImage(ext)) {
            throw new IllegalArgumentException("Path does not point to a recognised image file: " + relativePath);
        }

        Files.delete(target);
        log.info("Deleted orphaned image: {}", target);
    }
}

