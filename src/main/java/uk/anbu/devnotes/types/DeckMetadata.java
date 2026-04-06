package uk.anbu.devnotes.types;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record DeckMetadata(
        String theme,
        boolean paginate,
        List<Integer> headingDivider,
        String background,
        String cssClass,
        String lang
) {

    public static DeckMetadata empty() {
        return new DeckMetadata(null, false, List.of(), null, null, null);
    }

    public static DeckMetadata from(FrontMatter fm) {
        var extra = fm.extra();
        var theme = firstValue(extra, "theme");
        var paginate = "true".equalsIgnoreCase(firstValue(extra, "paginate"));
        var headingDivider = parseHeadingDivider(extra.getOrDefault("headingDivider", List.of()));
        var background = firstValue(extra, "background");
        var cssClass = firstValue(extra, "class");
        var lang = firstValue(extra, "lang");
        return new DeckMetadata(theme, paginate, headingDivider, background, cssClass, lang);
    }

    private static String firstValue(Map<String, List<String>> map, String key) {
        var values = map.get(key);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    private static List<Integer> parseHeadingDivider(List<String> values) {
        return values.stream()
                .flatMap(v -> java.util.Arrays.stream(v.split(",")))
                .map(String::trim)
                .filter(s -> s.matches("\\d+"))
                .map(Integer::parseInt)
                .filter(n -> n >= 1 && n <= 6)
                .collect(Collectors.toList());
    }
}