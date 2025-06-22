package uk.anbu.devnotes.types;

public record SearchCommand(String command, String restOfCommand, boolean caseSensitive) implements CommandInterface {
}
