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
    public ResponseEntity<String> encryptionKeyPage(
            @RequestParam(required = false) String returnTo) {
        return renderPage(null, returnTo);
    }

    /**
     * Accepts the user's passphrase, derives the AES-256 key via PBKDF2, re-encrypts all
     * datasource passwords, saves config, then redirects to {@code returnTo} (if safe) or
     * {@code /config/encryption-key} so the user sees the "key active" state with the change option.
     * On validation failure the form is re-rendered with an error message.
     */
    @PostMapping("/config/encryption-key")
    public ResponseEntity<String> setEncryptionKey(
            @RequestParam String passphrase,
            @RequestParam(required = false) String returnTo) {
        var saltFile = Path.of(configService.getDocsDirectory(), "config", "encryption.salt");
        try {
            encryptionService.setPassphrase(passphrase, saltFile);
            configService.reEncryptAndSave();
            var redirect = isSafeReturnTo(returnTo) ? returnTo : "/config/encryption-key";
            return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirect)
                .build();
        } catch (IllegalArgumentException e) {
            return renderPage(e.getMessage(), returnTo);
        } catch (Exception e) {
            log.error("Failed to activate encryption passphrase", e);
            return renderPage("Failed to activate passphrase: " + e.getMessage(), returnTo);
        }
    }

    /** Renders the change-passphrase form. */
    @GetMapping("/config/encryption-key/change")
    public ResponseEntity<String> changeEncryptionKeyPage(
            @RequestParam(required = false) String returnTo) {
        return renderChangePage(null, returnTo);
    }

    /**
     * Accepts a new passphrase, re-derives the AES-256 key via PBKDF2, re-encrypts all
     * datasource passwords, saves config, then redirects to {@code /config/encryption-key}.
     * On validation failure the change form is re-rendered with an error message.
     */
    @PostMapping("/config/encryption-key/change")
    public ResponseEntity<String> changeEncryptionKey(
            @RequestParam String passphrase,
            @RequestParam(required = false) String returnTo) {
        var saltFile = Path.of(configService.getDocsDirectory(), "config", "encryption.salt");
        try {
            encryptionService.setPassphrase(passphrase, saltFile);
            configService.reEncryptAndSave();
            return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "/config/encryption-key")
                .build();
        } catch (IllegalArgumentException e) {
            return renderChangePage(e.getMessage(), returnTo);
        } catch (Exception e) {
            log.error("Failed to change encryption passphrase", e);
            return renderChangePage("Failed to change passphrase: " + e.getMessage(), returnTo);
        }
    }

    /** Accepts only safe same-origin relative paths — prevents open-redirect abuse. */
    private static boolean isSafeReturnTo(String returnTo) {
        return returnTo != null && returnTo.startsWith("/") && !returnTo.contains("://");
    }

    private ResponseEntity<String> renderPage(String errorMessage, String returnTo) {
        var model = new HashMap<String, Object>();
        model.put("keySet", encryptionService.isKeySet());
        model.put("errorMessage", errorMessage);
        model.put("returnTo", returnTo != null ? returnTo : "");
        TemplateOutput output = new StringOutput();
        templateEngine.render("tools/encryption-key.jte", model, output);
        return ResponseEntity.status(HttpStatus.OK).body(output.toString());
    }

    private ResponseEntity<String> renderChangePage(String errorMessage, String returnTo) {
        var model = new HashMap<String, Object>();
        model.put("errorMessage", errorMessage);
        model.put("returnTo", returnTo != null ? returnTo : "");
        TemplateOutput output = new StringOutput();
        templateEngine.render("tools/encryption-key-change.jte", model, output);
        return ResponseEntity.status(HttpStatus.OK).body(output.toString());
    }
}

