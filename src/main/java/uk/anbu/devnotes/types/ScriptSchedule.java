package uk.anbu.devnotes.types;

public record ScriptSchedule(String id, String cronExpression, String scriptFile) {
}