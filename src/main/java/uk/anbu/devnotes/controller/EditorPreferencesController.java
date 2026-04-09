package uk.anbu.devnotes.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devnotes.service.EditorPreferencesService;

/**
 * REST endpoints for persisting editor UI preferences in server memory.
 *
 * <ul>
 *   <li>{@code GET  /editor/font-size} — returns the current font-size preference</li>
 *   <li>{@code POST /editor/font-size} — updates the font-size preference</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
@RestController
public class EditorPreferencesController {

    private final EditorPreferencesService editorPreferencesService;

    @GetMapping("/editor/font-size")
    public ResponseEntity<FontSizeResult> getFontSize() {
        return ResponseEntity.ok(new FontSizeResult(editorPreferencesService.getEditorFontSize()));
    }

    @PostMapping("/editor/font-size")
    public ResponseEntity<FontSizeResult> setFontSize(@RequestParam int fontSize) {
        if (!editorPreferencesService.setEditorFontSize(fontSize)) {
            log.warn("Rejected out-of-range font size: {}", fontSize);
            return ResponseEntity.badRequest().build();
        }
        log.debug("Editor font size updated to {}px", fontSize);
        return ResponseEntity.ok(new FontSizeResult(editorPreferencesService.getEditorFontSize()));
    }

    public record FontSizeResult(int fontSize) {}
}

