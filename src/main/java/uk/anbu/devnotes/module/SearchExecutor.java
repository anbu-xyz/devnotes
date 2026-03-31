package uk.anbu.devnotes.module;

import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import uk.anbu.devnotes.types.SearchResult;
import uk.anbu.devnotes.types.SearchResultList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public class SearchExecutor {
    private final String docsDirectory;

    public SearchResultList search(String searchPath, String searchParams,
                                   boolean caseSensitive) throws IOException {
        return search(searchPath, searchParams, caseSensitive, false);
    }

    public SearchResultList search(String searchPath, String searchParams,
                                   boolean caseSensitive, boolean regex) throws IOException {
        Path baseSearchPath = Path.of(docsDirectory);
        if (searchParams != null && !searchParams.isBlank()) {
            baseSearchPath = baseSearchPath.resolve(searchPath);
        }
        if (!baseSearchPath.toFile().isDirectory()) {
            throw new IOException("Invalid docs directory: " + docsDirectory);
        }
        var extensionsToSearch = new String[]{"md"};
        var files = FileUtils.listFiles(baseSearchPath.toFile(), extensionsToSearch, true);

        List<SearchResult> results = new ArrayList<>();

        if (regex) {
            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
            var pattern = Pattern.compile(searchParams, flags);
            for (File file : files) {
                String fileContent = FileUtils.readFileToString(file, "UTF-8");
                Matcher matcher = pattern.matcher(fileContent);
                if (matcher.find()) {
                    int start = Math.max(0, matcher.start() - 100);
                    int end = Math.min(fileContent.length(), matcher.end() + 100);
                    var preview = fileContent.substring(start, end) + "...\n";
                    String relativePath = new File(docsDirectory).toURI()
                            .relativize(file.toURI()).getPath();
                    results.add(new SearchResult(relativePath, preview));
                }
            }
        } else {
            String[] words = searchParams.split("\\s+");
            if (!caseSensitive) {
                words = toLowerCase(words);
            }

            for (File file : files) {
                String fileContent = FileUtils.readFileToString(file, "UTF-8");
                String searchContent = caseSensitive ? fileContent : fileContent.toLowerCase();

                StringBuilder preview = new StringBuilder();
                boolean allWordsFound = true;

                for (String word : words) {
                    int index = searchContent.indexOf(word);
                    if (index == -1) {
                        allWordsFound = false;
                        break;
                    }
                }

                if (allWordsFound) {
                    int index = searchContent.indexOf(words[0]);
                    int start = Math.max(0, index - 100);
                    int end = Math.min(searchContent.length(), index + words[0].length() + 100);
                    preview.append(fileContent, start, end).append("...\n");

                    // find relative path to file
                    String relativePath = new File(docsDirectory).toURI()
                            .relativize(file.toURI()).getPath();

                    results.add(new SearchResult(relativePath, preview.toString()));
                }
            }
        }

        return new SearchResultList(Arrays.asList(extensionsToSearch), results);
    }

    private static String[] toLowerCase(String[] words) {
        String[] result = new String[words.length];
        for (int i = 0; i < words.length; i++) {
            result[i] = words[i].toLowerCase();
        }
        return result;
    }
}
