package uk.anbu.devnotes.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devnotes.module.JsFileFetcher;

@Slf4j
@RequiredArgsConstructor
@RestController
public class JsController {
    private final JsFileFetcher jsFileFetcher;

    @GetMapping("/js/**")
    public ResponseEntity<Resource> image(HttpServletRequest request) {

        try {
            String filename = request.getRequestURI().substring("/js".length());
            var resource = jsFileFetcher.jsFile("", filename);

            if (resource.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            } else {
                return ResponseEntity.ok()
                        .contentType(org.springframework.http.MediaType.parseMediaType("text/javascript"))
                        .body(resource.get());
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}
