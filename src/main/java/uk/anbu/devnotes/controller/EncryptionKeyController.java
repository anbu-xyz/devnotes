package uk.anbu.devnotes.controller;

import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devnotes.service.ConfigServiceImpl;
import uk.anbu.devnotes.service.EncryptionService;

import java.nio.file.Path;
import java.util.HashMap;

@RestController
@RequiredArgsConstructor
@Slf4j
public class EncryptionKeyController {

    private final TemplateEngine    templateEngine;
    private final EncryptionService encryptionService;
    private final ConfigServiceImpl configService;

    /** Renders the passphrase-entry form. */
    @GetMapping("/config/encryption-key")
    public ResponseEntity<String> encryptionKeyPage() {
        return renderPage(null);
    }

    /**
     * Accepts the user's passphrase, derives the AES-256 key via PBKDF2, re-encrypts all
     * datasource passwords, saves config, then redirects to {@code /config}.
     * On validation failure the form is re-rendered with an error message.
     */
    @PostMapping("/config/encryption-key")
    public ResponseEntity<String> setEncryptionKey(@RequestParam String passphrase) {
        Path saltFile = Path.of(configService.getDocsDirectory(), "config", "encryption.salt");
        try {
            encryptionService.setPassphrase(passphrase, saltFile);
            configService.reEncryptAndSave();
            return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "/config")
                .build();
        } catch (IllegalArgumentException e) {
            return renderPage(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to activate encryption passphrase", e);
            return renderPage("Failed to activate passphrase: " + e.getMessage());
        }
    }

    private ResponseEntity<String> renderPage(String errorMessage) {
        var model = new HashMap<String, Object>();
        model.put("keySet", encryptionService.isKeySet());
        model.put("errorMessage", errorMessage);
        TemplateOutput output = new StringOutput();
        templateEngine.render("tools/encryption-key.jte", model, output);
        return ResponseEntity.status(HttpStatus.OK).body(output.toString());
    }
}

