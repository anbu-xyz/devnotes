package uk.anbu.devnotes.util;

import lombok.extern.slf4j.Slf4j;
import uk.anbu.devnotes.types.MarkdownFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Slf4j
public class FileBasedCache {

    public static String generateCacheFileName(MarkdownFile markdownFile, String scriptText) {
        String hash = generateHash(scriptText);
        var fileName = Paths.get(markdownFile.fileName()).getFileName().toString();
        var generatedFileName = fileName.replaceFirst("[.][^.]+$", "") + "." + hash + ".outputString";
        return markdownFile.fullPath().getParent().resolve(generatedFileName).toString();
    }

    private static String generateHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.substring(0, 16); // Use first 16 characters of the hash
        } catch (NoSuchAlgorithmException e) {
            log.error("Error generating hash", e);
            return "error";
        }
    }

    public static String readFromFile(Path outputFile, String outputFileName) {
        String output;
        try {
            output = Files.readString(outputFile);
            log.info("Using existing outputString file: {}", outputFileName);
        } catch (IOException e) {
            log.error("Error reading existing outputString file: {}", outputFileName, e);
            output = "Error: Unable to read existing outputString file";
        }
        return output;
    }

    public static void saveOutput(Path outputFile, String content) {
        try {
            Files.createDirectories(outputFile.getParent());
            Files.write(outputFile, content.getBytes());
        } catch (IOException e) {
            log.error("Error saving outputString file", e);
        }
    }

}
