package uk.anbu.devnotes.types;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlashCard {

    // --- Persisted fields (written to / read from YAML) ---

    private String question;
    private String answer;
    private LocalDateTime lastReviewed;
    private LocalDateTime nextReview;
    private int reviewCount;
    private int correctCount;
    private int incorrectCount;
    private double easeFactor = 2.5;
    private int interval = 1;

    // --- Transient runtime fields (never serialised to YAML) ---

    /** Relative path from the flashcards root, e.g. {@code "java/streams/lambda-basics.yaml"}. */
    @JsonIgnore
    private String relativePath;

    /** Parent directory relative to the flashcards root, e.g. {@code "java/streams"}. */
    @JsonIgnore
    private String topic;
}

