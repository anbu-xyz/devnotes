package uk.anbu.devnotes.types;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record FrontMatter(
        String title,
        List<String> tags,
        String description,
        Map<String, List<String>> extra
) {

    public static FrontMatter empty() {
        return new FrontMatter(null, List.of(), null, Map.of());
    }

    public static FrontMatter from(Map<String, List<String>> data) {
        if (data == null || data.isEmpty()) {
            return empty();
        }
        var title = firstValue(data, "title");
        var tags = List.copyOf(data.getOrDefault("tags", List.of()));
        var description = firstValue(data, "description");
        var extra = new LinkedHashMap<>(data);
        extra.remove("title");
        extra.remove("tags");
        extra.remove("description");
        return new FrontMatter(title, tags, description, Map.copyOf(extra));
    }

    private static String firstValue(Map<String, List<String>> data, String key) {
        var values = data.get(key);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    public boolean isEmpty() {
        return title == null && tags.isEmpty() && description == null && extra.isEmpty();
    }
}