package uk.anbu.devnotes.module;

import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import uk.anbu.devnotes.types.SearchResult;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class SearchExecutor {
    private final String docsDirectory;

    public List<SearchResult> search(String searchParams, boolean caseSensitive) throws IOException {
        if (!new File(docsDirectory).isDirectory()) {
            throw new IOException("Invalid docs directory: " + docsDirectory);
        }
        var files = FileUtils.listFiles(new File(docsDirectory), new String[]{"md"}, true);
        String[] words = searchParams.split("\\s+");

        if (!caseSensitive) {
            words = toLowerCase(words);
        }

        List<SearchResult> results = new ArrayList<>();
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
                // Use first word for preview context
                int index = searchContent.indexOf(words[0]);
                int start = Math.max(0, index - 100);
                int end = Math.min(searchContent.length(), index + words[0].length() + 100);
                preview.append(fileContent.substring(start, end)).append("...\n");

                results.add(new SearchResult(file.getName(), preview.toString()));
            }
        }
        return results;
    }

    private static String[] toLowerCase(String[] words) {
        String[] result = new String[words.length];
        for (int i = 0; i < words.length; i++) {
            result[i] = words[i].toLowerCase();
        }
        return result;
    }
}
