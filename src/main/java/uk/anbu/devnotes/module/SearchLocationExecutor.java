package uk.anbu.devnotes.module;

import lombok.RequiredArgsConstructor;
import uk.anbu.devnotes.types.SearchResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RequiredArgsConstructor
public class SearchLocationExecutor {

    private final Path docsDir;
    private final String searchParam;

    public List<SearchResult> getResult() throws IOException {
        final String searchParamLowercase = searchParam.trim().toLowerCase();
        final String[] searchTerms = searchParamLowercase.split("\\s+");

        List<SearchResult> results;

        try (var files = Files.walk(docsDir)) {
            results = files.filter(Files::isRegularFile)
                    .map(path -> pathInfo(path, docsDir))
                    .filter(pathInfo -> matchInOrder(pathInfo, searchTerms))
                    .map(pathInfo -> new SearchResult(pathInfo.standardizedPath(), ""))
                    .toList();
        }
        return results;
    }

    private static boolean matchInOrder(PathInfo pathInfo, String[] searchTerms) {
        int lastIndex = -1;
        for (String term : searchTerms) {
            int currentIndex = pathInfo.standardizedPath().toLowerCase().indexOf(term, lastIndex + 1);
            if (currentIndex == -1) {
                return false;
            }
            lastIndex = currentIndex;
        }
        return true;
    }

    private static PathInfo pathInfo(Path path, Path docsDir) {
        final String relativePath = docsDir.relativize(path).toString();
        return new PathInfo(relativePath.replace('\\', '/'), relativePath);
    }

    private record PathInfo(String standardizedPath, String originalPath) {}
}
