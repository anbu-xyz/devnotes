package uk.anbu.devnotes.markdown.slides;

import lombok.extern.slf4j.Slf4j;
import uk.anbu.devnotes.markdown.FrontMatterParser;
import uk.anbu.devnotes.types.DeckMetadata;
import uk.anbu.devnotes.types.Slide;
import uk.anbu.devnotes.types.SlideMetadata;
import uk.anbu.devnotes.types.SlidesDeck;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public final class SlidesParser {

    private static final Pattern SEPARATOR = Pattern.compile("(?m)^---$");

    private SlidesParser() {
    }

    /**
     * Parses a raw markdown string (possibly with YAML front-matter) into a {@link SlidesDeck}.
     * <p>
     * Algorithm:
     * <ol>
     *   <li>Parse YAML front-matter → {@link DeckMetadata}.</li>
     *   <li>Strip the front-matter block from the text.</li>
     *   <li>Split on {@code ---} separators to get raw segments.</li>
     *   <li>Walk segments: a segment that parses as {@link SlideMetadata} is held as pending
     *       metadata for the next slide; all other non-blank segments become slide bodies.</li>
     *   <li>Apply heading-divider splitting within each body segment.</li>
     *   <li>Extract {@code note:} speaker-note lines from each body.</li>
     * </ol>
     */
    public static SlidesDeck parse(String rawMarkdown) {
        if (rawMarkdown == null || rawMarkdown.isBlank()) {
            return new SlidesDeck(DeckMetadata.empty(), List.of());
        }

        var frontMatter = FrontMatterParser.parseText(rawMarkdown);
        var deckMetadata = DeckMetadata.from(frontMatter);
        var body = stripFrontMatter(rawMarkdown);

        var segments = Arrays.stream(SEPARATOR.split(body, -1))
                .map(String::trim)
                .collect(Collectors.toList());

        var slides = new ArrayList<Slide>();
        var slideIndex = 0;
        SlideMetadata pendingMeta = null;

        for (var segment : segments) {
            var metaOpt = SlideMetadata.tryParse(segment);
            if (metaOpt.isPresent()) {
                pendingMeta = metaOpt.get();
            } else if (!segment.isBlank()) {
                var meta = (pendingMeta != null) ? pendingMeta : SlideMetadata.empty();
                pendingMeta = null;

                var subRegions = applyHeadingDivider(segment, deckMetadata.headingDivider());
                var firstRegion = true;
                for (var region : subRegions) {
                    if (region.isBlank()) {
                        firstRegion = false;
                        continue;
                    }
                    var slideMeta = firstRegion ? meta : SlideMetadata.empty();
                    firstRegion = false;
                    var notes = combineNotes(slideMeta.notes(), extractRevealNotes(region));
                    var body2 = removeRevealNoteLines(region);
                    slides.add(new Slide(slideIndex++, body2.strip(), slideMeta, notes));
                }
            }
        }

        return new SlidesDeck(deckMetadata, List.copyOf(slides));
    }

    // ─── Front-matter stripping ──────────────────────────────────────────────

    static String stripFrontMatter(String markdown) {
        if (!markdown.startsWith("---")) {
            return markdown;
        }
        // Find the closing --- after the opening one
        var close = markdown.indexOf("\n---", 3);
        if (close < 0) {
            return markdown;
        }
        var after = close + 4; // skip "\n---"
        if (after < markdown.length() && markdown.charAt(after) == '\n') {
            after++;
        }
        return markdown.substring(after);
    }

    // ─── Heading-divider splitting ───────────────────────────────────────────

    static List<String> applyHeadingDivider(String text, List<Integer> levels) {
        if (levels.isEmpty()) {
            return List.of(text);
        }
        // Build lookahead regex: split just before any matching heading line
        var alternation = levels.stream()
                .map(n -> "^#{" + n + "} ")
                .collect(Collectors.joining("|"));
        var splitPattern = Pattern.compile("(?m)(?=^(?:" + alternation + "))", Pattern.MULTILINE);
        var parts = splitPattern.split(text, -1);
        return Arrays.stream(parts)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }

    // ─── Speaker-note extraction ─────────────────────────────────────────────

    /**
     * Extracts text following a bare {@code note:} line (reveal-style notes).
     * Returns an empty string when no such line is present.
     */
    static String extractRevealNotes(String content) {
        var lines = content.split("\n", -1);
        for (var i = 0; i < lines.length; i++) {
            if (lines[i].trim().equals("note:")) {
                return Arrays.stream(lines, i + 1, lines.length)
                        .collect(Collectors.joining("\n"))
                        .strip();
            }
        }
        return "";
    }

    /**
     * Removes the {@code note:} section (and everything after it) from visible slide content.
     */
    static String removeRevealNoteLines(String content) {
        var lines = content.split("\n", -1);
        for (var i = 0; i < lines.length; i++) {
            if (lines[i].trim().equals("note:")) {
                return Arrays.stream(lines, 0, i)
                        .collect(Collectors.joining("\n"));
            }
        }
        return content;
    }

    private static String combineNotes(String metaNotes, String revealNotes) {
        if (metaNotes != null && !metaNotes.isBlank() && !revealNotes.isBlank()) {
            return metaNotes.strip() + "\n" + revealNotes;
        }
        if (metaNotes != null && !metaNotes.isBlank()) {
            return metaNotes.strip();
        }
        return revealNotes;
    }
}