package uk.anbu.devnotes.types;

/**
 * Aggregate statistics for all flash cards that share a given topic (directory path).
 *
 * @param topic           directory path relative to the flashcards root, e.g. {@code "java/streams"}
 * @param total           total number of cards in this topic
 * @param dueNow          cards whose {@code nextReview} is null or &le; now
 * @param reviewedToday   cards whose {@code lastReviewed} date equals today
 * @param accuracyPercent percentage of all reviews that were correct (0–100), or 0 when never reviewed
 * @param currentStreak   count of the most recent consecutive correct reviews across cards in this topic
 */
public record FlashCardStats(
        String topic,
        int total,
        int dueNow,
        int reviewedToday,
        double accuracyPercent,
        int currentStreak
) {}

