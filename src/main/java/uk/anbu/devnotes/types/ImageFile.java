package uk.anbu.devnotes.types;

import java.nio.file.Path;
import java.nio.file.Paths;

public record ImageFile(String path, String filename, String docsDirectory) {

    public boolean exists() {
        return fullPath().toFile().exists();
    }

    public Path fullPath() {
        Path markdownRoot = Paths.get(docsDirectory);
        return markdownRoot.resolve(path == null ? "" : path)
                .resolve(filename.replaceFirst("^/", ""));
    }
}
