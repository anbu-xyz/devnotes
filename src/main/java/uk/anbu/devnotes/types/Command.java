package uk.anbu.devnotes.types;

public record Command(String command, String restOfCommand) implements CommandInterface {
}
