package uk.anbu.devnotes.types;

import java.util.List;

public record SearchResultList(List<String> extensions, List<SearchResult> results) {
}
