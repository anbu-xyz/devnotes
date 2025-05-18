package uk.anbu.devnotes.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devnotes.module.PlantumlRenderer;

@Slf4j
@RequiredArgsConstructor
@RestController
public class PlantumlController {
    private final PlantumlRenderer plantumlRenderer;

    @GetMapping("/plantuml")
    public ResponseEntity<Resource> renderPlantuml(@RequestParam("filename") String filename) {
        try {
            var resource = plantumlRenderer.renderPlantuml("", filename);

            if (resource.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            } else {
                return ResponseEntity.ok()
                        .contentType(org.springframework.http.MediaType.IMAGE_PNG)
                        .body(resource.get());
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}
