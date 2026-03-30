package uk.anbu.devnotes.service;

import uk.anbu.devnotes.types.FlashCard;

import java.time.LocalDateTime;

import static java.time.ZoneOffset.UTC;

/**
 * Pure, stateless implementation of the SM-2 spaced-repetition algorithm.
 *
 * <p>The single public method {@link #apply(FlashCard, int)} accepts a card and a quality
 * rating (0–5), and returns a <em>new</em> {@link FlashCard} with updated scheduling fields.
 * The input card is never mutated.  This class has no Spring dependencies and no I/O, making
 * it trivially unit-testable.
 *
 * <h2>Quality rating labels</h2>
 * <table border="1">
 *   <tr><th>Rating</th><th>Label</th><th>Meaning</th></tr>
 *   <tr><td>0</td><td>Blackout</td><td>Complete blank — no memory at all</td></tr>
 *   <tr><td>1</td><td>Wrong</td><td>Incorrect response, remembered after seeing answer</td></tr>
 *   <tr><td>2</td><td>Forgot</td><td>Incorrect but easy when shown the answer</td></tr>
 *   <tr><td>3</td><td>Hard</td><td>Correct with significant difficulty</td></tr>
 *   <tr><td>4</td><td>Good</td><td>Correct after some hesitation</td></tr>
 *   <tr><td>5</td><td>Easy</td><td>Perfect response, no hesitation</td></tr>
 * </table>
 */
public class Sm2Algorithm {

    private Sm2Algorithm() {
        // utility class — not instantiable
    }

    /**
     * Apply the SM-2 algorithm to a flash card.
     *
     * @param card    the source card (never mutated)
     * @param quality recall quality rating in the range [0, 5]
     * @return a new {@link FlashCard} with updated scheduling fields
     * @throws IllegalArgumentException if {@code quality} is outside [0, 5]
     */
    public static FlashCard apply(FlashCard card, int quality) {
        if (quality < 0 || quality > 5) {
            throw new IllegalArgumentException(
                    "Quality must be between 0 and 5 inclusive, got: " + quality);
        }

        FlashCard updated = copyCard(card);
        LocalDateTime now = LocalDateTime.now(UTC);

        if (quality >= 3) {
            // ---- Correct recall ----
            int newInterval;
            if (updated.getReviewCount() == 0) {
                newInterval = 1;
            } else if (updated.getReviewCount() == 1) {
                newInterval = 6;
            } else {
                newInterval = (int) Math.round(updated.getInterval() * updated.getEaseFactor());
            }

            // SM-2 ease-factor update formula
            double newEF = updated.getEaseFactor()
                    + 0.1
                    - (5 - quality) * (0.08 + (5 - quality) * 0.02);
            newEF = Math.max(1.3, newEF);

            updated.setInterval(newInterval);
            updated.setEaseFactor(newEF);
            updated.setReviewCount(updated.getReviewCount() + 1);
            updated.setCorrectCount(updated.getCorrectCount() + 1);
        } else {
            // ---- Failed recall ----
            // Reset interval and review streak; cumulative history counts are preserved.
            // EF is intentionally left unchanged to avoid double-penalising on failure.
            updated.setInterval(1);
            updated.setReviewCount(0);
            updated.setIncorrectCount(updated.getIncorrectCount() + 1);
        }

        updated.setLastReviewed(now);
        updated.setNextReview(now.plusDays(updated.getInterval()));

        return updated;
    }

    // ---------- helpers ----------

    private static FlashCard copyCard(FlashCard src) {
        FlashCard copy = new FlashCard();
        copy.setQuestion(src.getQuestion());
        copy.setAnswer(src.getAnswer());
        copy.setLastReviewed(src.getLastReviewed());
        copy.setNextReview(src.getNextReview());
        copy.setReviewCount(src.getReviewCount());
        copy.setCorrectCount(src.getCorrectCount());
        copy.setIncorrectCount(src.getIncorrectCount());
        copy.setEaseFactor(src.getEaseFactor());
        copy.setInterval(src.getInterval());
        copy.setRelativePath(src.getRelativePath());
        copy.setTopic(src.getTopic());
        return copy;
    }
}

