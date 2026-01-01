package uk.anbu.devnotes.module;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import uk.anbu.devnotes.types.SearchResult;
import uk.anbu.devnotes.types.SearchResultList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.PriorityQueue;

@RequiredArgsConstructor
public class RecentlyModifiedExecutor {

    private final Path docsDir;

    public SearchResultList getResult() throws IOException {
        // Keep a bounded min-heap of size 20 based on last-modified time (ascending). This
        // ensures we only retain the 20 most-recently modified files while streaming the walk.
        final int MAX_RESULTS = 20;
        var extensionsToSearch = List.of("md");

        PriorityQueue<Item> heap = new PriorityQueue<>(Comparator.comparingLong(i -> i.lastModified));

        try (var files = Files.walk(docsDir)) {
            files.filter(Files::isRegularFile)
                    .forEach(path -> fillHeap(path, extensionsToSearch, heap, MAX_RESULTS));
        }

        List<SearchResult> results = new ArrayList<>(heap.size());
        ArrayList<Item> items = new ArrayList<>(heap);
        items.sort(Comparator.comparingLong((Item i) -> i.lastModified).reversed());
        for (Item it : items) {
            results.add(new SearchResult(it.standardizedPath, ""));
        }

        return new SearchResultList(extensionsToSearch, results);
    }

    @SneakyThrows
    private void fillHeap(Path path, List<String> extensionsToSearch,
                          PriorityQueue<Item> heap, int maxResult) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot == -1) {
            return;
        }
        String ext = fileName.substring(dot + 1);
        if (!extensionsToSearch.contains(ext)) {
            return;
        }

        long lastModified = Files.getLastModifiedTime(path).toMillis();

        // compute standardized path (relative, forward slashes)
        final String relativePath = docsDir.relativize(path).toString();
        final String standardized = relativePath.replace('\\', '/');

        Item item = new Item(lastModified, standardized);
        if (heap.size() < maxResult) {
            heap.add(item);
        } else if (heap.peek() != null && heap.peek().lastModified < lastModified) {
            heap.poll();
            heap.add(item);
        }
    }

    private record Item(long lastModified, String standardizedPath) {
    }
}
