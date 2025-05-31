package uk.anbu.devnotes.types;

import java.nio.file.Path;

public record MarkdownFile(Path docsDirectory, String fileName) {
    public boolean exists() {
        return fullPath().toFile().exists();
    }

    public Path fullPath() {
        return docsDirectory.resolve(fileName);
    }
}
