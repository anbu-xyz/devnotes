package uk.anbu.devnotes.types;

import java.util.List;

public record RecentlyModifiedList(List<String> extensions, List<RecentlyModifiedListEntry> results) {
}
