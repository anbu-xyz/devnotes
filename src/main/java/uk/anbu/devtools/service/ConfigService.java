package uk.anbu.devtools.service;

import org.springframework.stereotype.Service;

@Service
public class ConfigService {

    private String markdownDirectory;

    public ConfigService() {
        // Set a default value or load from a properties file
        this.markdownDirectory = "//wsl.localhost/Ubuntu/home/anbu/workspace/docs";
    }

    public String getMarkdownDirectory() {
        return markdownDirectory;
    }

    public void setMarkdownDirectory(String markdownDirectory) {
        this.markdownDirectory = markdownDirectory;
    }
}
