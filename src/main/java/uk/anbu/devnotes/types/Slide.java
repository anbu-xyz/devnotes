package uk.anbu.devnotes.types;

public record Slide(
        int slideIndex,
        String rawMarkdown,
        SlideMetadata metadata,
        String speakerNotes
) {
}