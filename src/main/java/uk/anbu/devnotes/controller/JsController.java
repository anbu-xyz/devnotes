package uk.anbu.devnotes.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devnotes.module.JsFileFetcher;

@Slf4j
@RequiredArgsConstructor
@RestController
public class JsController {
    private final JsFileFetcher jsFileFetcher;

    @GetMapping("/javascript")
    public ResponseEntity<Resource> image(HttpServletRequest request, @RequestParam(name = "filename", required = false) String filename) {

        try {
            String refererFileName = null;
            if (filename.startsWith("./")) {
                refererFileName = findRefererFileName(request);
            }
            String refererDirectory = refererFileName != null ? refererFileName.substring(0, refererFileName.lastIndexOf('/')) : null;
            var resource = jsFileFetcher.jsFile(refererDirectory, filename);

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

    private static String findRefererFileName(HttpServletRequest request) {
        String referer = request.getHeader("referer");
        if (referer != null) {
            String parameters = referer.substring(referer.lastIndexOf('?') + 1);
            for (String param : parameters.split("&")) {
                if (param.startsWith("filename=")) {
                    return param.substring("filename=".length());
                }
            }
        }
        return null;
    }
}
