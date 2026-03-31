package uk.anbu.devnotes.markdown;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans every {@code ```todo} fenced code block in a markdown document and, for every list
 * item that is missing a {@code created:} field, injects {@code created: <today>} immediately
 * after the item's opening line.
 * <p>
 * This class is stateless; all methods are static.
 */
@Slf4j
public class TodoCreatedDateFiller {

    /**
     * Matches a complete {@code ```todo} fenced block.
     * <ul>
     *   <li>Group 1 – the opening line including the trailing newline</li>
     *   <li>Group 2 – the raw YAML body between the fences</li>
     *   <li>Group 3 – the newline + closing fence</li>
     * </ul>
     */
    private static final Pattern TODO_FENCE =
            Pattern.compile("(```todo[^\\r\\n]*\\r?\\n)(.*?)(\\r?\\n[ \\t]*```)", Pattern.DOTALL);

    private TodoCreatedDateFiller() {}

    /**
     * Returns a copy of {@code markdown} where every {@code ```todo} block has
     * {@code created: <today>} injected into any item that is missing it.
     */
    public static String fillMissingCreatedDates(String markdown, LocalDate today) {
        var matcher = TODO_FENCE.matcher(markdown);
        var sb = new StringBuilder();
        while (matcher.find()) {
            var filled = injectCreatedDates(matcher.group(2), today);
            matcher.appendReplacement(sb,
                    Matcher.quoteReplacement(matcher.group(1) + filled + matcher.group(3)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Processes the raw YAML body of a single {@code ```todo} fence.
     * <p>
     * For each item in the {@code items:} list that has no {@code created:} property at the
     * expected indentation level, inserts {@code created: <today>} immediately after the
     * item's opening {@code - } line.
     * <p>
     * CRLF line endings are detected and preserved.
     */
    static String injectCreatedDates(String yamlBody, LocalDate today) {
        var todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
        boolean hasCr = yamlBody.contains("\r\n");
        var normalised = yamlBody.replace("\r\n", "\n");
        var lines = new ArrayList<>(Arrays.asList(normalised.split("\n", -1)));

        boolean inItems = false;
        int itemIndent = -1;          // indent of the "- " markers once first item is seen
        int currentItemFirstLine = -1;
        boolean currentItemHasCreated = false;
        var insertAfterLines = new ArrayList<Integer>();

        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            var stripped = line.stripLeading();
            int indent = line.length() - stripped.length();

            if (stripped.isEmpty()) continue;

            if (!inItems) {
                if (stripped.equals("items:")) {
                    inItems = true;
                    itemIndent = -1;
                    currentItemHasCreated = false;
                }
                continue;
            }

            boolean isItemStart = stripped.startsWith("- ") || stripped.equals("-");

            // Detect leaving the items section: a non-item line at or above the item indent.
            // When itemIndent is not yet known (== -1), treat any indent-0 line as the boundary.
            boolean leavingItems = itemIndent >= 0
                    ? (indent <= itemIndent && !isItemStart)
                    : (indent == 0);

            if (leavingItems) {
                if (currentItemFirstLine >= 0 && !currentItemHasCreated) {
                    insertAfterLines.add(currentItemFirstLine);
                }
                currentItemFirstLine = -1;
                inItems = false;
                // A second `items:` block is theoretically possible
                if (stripped.equals("items:")) {
                    inItems = true;
                    itemIndent = -1;
                    currentItemHasCreated = false;
                }
                continue;
            }

            if (isItemStart && (itemIndent == -1 || indent == itemIndent)) {
                // Start of a new list item — close the previous one if missing created
                if (currentItemFirstLine >= 0 && !currentItemHasCreated) {
                    insertAfterLines.add(currentItemFirstLine);
                }
                itemIndent = indent;
                currentItemFirstLine = i;
                currentItemHasCreated = false;
            } else if (currentItemFirstLine >= 0
                    && stripped.startsWith("created:")
                    && indent == itemIndent + 2) {
                // Direct `created:` property of the current item (not a nested key)
                currentItemHasCreated = true;
            }
        }

        // Close the last item
        if (inItems && currentItemFirstLine >= 0 && !currentItemHasCreated) {
            insertAfterLines.add(currentItemFirstLine);
        }

        if (insertAfterLines.isEmpty()) {
            return yamlBody;
        }

        // Insert in reverse order so earlier line indices remain valid
        var propIndent = " ".repeat(itemIndent >= 0 ? itemIndent + 2 : 4);
        for (int i = insertAfterLines.size() - 1; i >= 0; i--) {
            lines.add(insertAfterLines.get(i) + 1, propIndent + "created: " + todayStr);
        }

        var result = String.join("\n", lines);
        return hasCr ? result.replace("\n", "\r\n") : result;
    }
}

