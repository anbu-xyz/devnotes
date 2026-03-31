package uk.anbu.devnotes.controller;

import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devnotes.service.ImageAuditService;

import java.util.HashMap;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ImageAuditController {

    private final ImageAuditService imageAuditService;
    private final TemplateEngine templateEngine;

    @GetMapping("/tools/image-audit")
    public ResponseEntity<String> imageAuditPage() {
        try {
            var result = imageAuditService.audit();
            var model = new HashMap<String, Object>();
            model.put("orphanedImages", result.orphanedImages());
            model.put("brokenLinks", result.brokenLinks());
            TemplateOutput output = new StringOutput();
            templateEngine.render("tools/image-audit.jte", model, output);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(output.toString());
        } catch (Exception e) {
            log.error("Error performing image audit", e);
            return ResponseEntity.internalServerError()
                    .body("Error performing image audit: " + e.getMessage());
        }
    }
}

