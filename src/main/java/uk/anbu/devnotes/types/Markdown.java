package uk.anbu.devnotes.types;

public record Markdown(String text) {
    public Markdown(byte[] bytes) {
        this(new String(bytes));
    }
}
