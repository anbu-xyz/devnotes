package uk.anbu.devnotes.service;

import org.springframework.stereotype.Service;

/**
 * Holds in-memory editor UI preferences (singleton - single-user personal tool).
 * Values survive for the lifetime of the JVM but are not persisted to disk.
 */
@Service
public class EditorPreferencesService {

    public static final int DEFAULT_FONT_SIZE = 14;
    public static final int MIN_FONT_SIZE = 10;
    public static final int MAX_FONT_SIZE = 28;

    private volatile int editorFontSize = DEFAULT_FONT_SIZE;

    public int getEditorFontSize() {
        return editorFontSize;
    }

    /**
     * Updates the stored font size.
     *
     * @param fontSize desired size in pixels
     * @return {@code true} when accepted; {@code false} when out of the allowed range
     */
    public boolean setEditorFontSize(int fontSize) {
        if (fontSize < MIN_FONT_SIZE || fontSize > MAX_FONT_SIZE) {
            return false;
        }
        this.editorFontSize = fontSize;
        return true;
    }
}

