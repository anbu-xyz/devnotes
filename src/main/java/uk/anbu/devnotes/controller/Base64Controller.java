package uk.anbu.devnotes.controller;

import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;

@RestController
@RequiredArgsConstructor
@Slf4j
public class Base64Controller {
    private final TemplateEngine templateEngine;

    @GetMapping("/base64")
    public ResponseEntity<String> base64() {
        var model = new HashMap<String, Object>();
        TemplateOutput output = new StringOutput();
        templateEngine.render("tools/base64.jte", model, output);
        return ResponseEntity.status(HttpStatus.OK)
                .body(output.toString());
    }

    // read post params from request body
    @PostMapping("/base64")
    public ResponseEntity<String> base64(@RequestBody EncodeToFileRequest encodeToFileRequest) {
        var encodedData = Base64.getDecoder().decode(encodeToFileRequest.encodedText);

        // get system temp directory
        var tempDir = Paths.get(System.getProperty("java.io.tmpdir"));
        var filePath = tempDir.resolve(encodeToFileRequest.decodeToFile);
        try {
            if (!Files.exists(filePath)) {
                Files.createFile(filePath);
            }
            Files.write(filePath, encodedData);
        } catch (IOException e) {
            log.error("Error writing to file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error writing to file");
        }

        return ResponseEntity.status(HttpStatus.OK)
                .body("written to file: " + filePath.toAbsolutePath());
    }

    public static class EncodeToFileRequest {
        public String encodedText;
        public String decodeToFile;
    }
}
