package uk.anbu.devnotes.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devnotes.util.GroovyShellRunner;

@Slf4j
@RequiredArgsConstructor
@RestController
public class GroovyController {

    @GetMapping("/groovyToText")
    public ResponseEntity<String> groovyToText(@RequestBody String groovyScript) {
        var contentType = org.springframework.http.MediaType.TEXT_PLAIN;
        return getStringResponseEntity(groovyScript, contentType);
    }

    @GetMapping("/groovyToHtml")
    public ResponseEntity<String> groovyToHtml(@RequestBody String groovyScript) {
        var contentType = org.springframework.http.MediaType.TEXT_HTML;
        return getStringResponseEntity(groovyScript, contentType);
    }

    private static ResponseEntity<String> getStringResponseEntity(String groovyScript, MediaType contentType) {
        try {
            var result = GroovyShellRunner.execute(groovyScript);
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .body(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}
