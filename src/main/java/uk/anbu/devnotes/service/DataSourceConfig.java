package uk.anbu.devnotes.service;

public record DataSourceConfig(String name, String url, String username, String password, String driverClassName) {
}
