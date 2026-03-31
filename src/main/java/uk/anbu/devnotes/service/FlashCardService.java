package uk.anbu.devnotes.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Service;
import uk.anbu.devnotes.types.FlashCard;
import uk.anbu.devnotes.types.FlashCardStats;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.time.ZoneOffset.UTC;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlashCardService {

    private final ConfigService configService;

    // -------------------------------------------------------------------------
    // Directory management
    // -------------------------------------------------------------------------

    @SneakyThrows
    public Path flashcardsRoot() {
        Path root = Path.of(configService.getDocsDirectory(), "config", "flashcards");
        if (!Files.exists(root)) {
            Files.createDirectories(root);
            Files.writeString(root.resolve(".gitkeep"), "", StandardCharsets.UTF_8);
            log.info("Created flashcards directory at {}", root);
        }
        return root;
    }

    // -------------------------------------------------------------------------
    // Card I/O
    // -------------------------------------------------------------------------

    @SneakyThrows
    public List<FlashCard> loadAllCards() {
        Path root = flashcardsRoot();
        List<FlashCard> cards = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> p.toString().endsWith(".yaml")
                           && !p.getFileName().toString().startsWith("."))
                 .forEach(p -> {
                     FlashCard card = loadCardFromPath(root, p);
                     if (card != null) {
                         cards.add(card);
                     }
                 });
        }
        return cards;
    }

    public Optional<FlashCard> loadCard(String relativePath) {
        Path root = flashcardsRoot();
        Path cardPath = root.resolve(relativePath);
        if (!Files.exists(cardPath)) {
            return Optional.empty();
        }
        return Optional.ofNullable(loadCardFromPath(root, cardPath));
    }

    @SneakyThrows
    public void saveCard(FlashCard card) {
        Path root = flashcardsRoot();
        Path cardPath = root.resolve(card.getRelativePath());
        Files.createDirectories(cardPath.getParent());
        createMapper().writeValue(cardPath.toFile(), card);
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    /**
     * Picks up to {@code n} cards for a quick "random" review session.
     * Due cards (nextReview null or past) are always included first (shuffled for variety).
     * Future cards fill any remaining slots, ordered soonest-due first.
     */
    public List<FlashCard> pickRandomCards(String topicFilter, int n) {
        if (n <= 0) return Collections.emptyList();
        LocalDateTime now = LocalDateTime.now(UTC);

        List<FlashCard> all = loadAllCards().stream()
                .filter(card -> matchesTopic(card, topicFilter))
                .collect(Collectors.toList());

        List<FlashCard> due = new ArrayList<>(all.stream()
                .filter(c -> c.getNextReview() == null || !c.getNextReview().isAfter(now))
                .collect(Collectors.toList()));

        List<FlashCard> future = all.stream()
                .filter(c -> c.getNextReview() != null && c.getNextReview().isAfter(now))
                .sorted(Comparator.comparing(FlashCard::getNextReview))
                .collect(Collectors.toList());

        // Shuffle due cards so each session feels fresh
        Collections.shuffle(due);

        List<FlashCard> result = new ArrayList<>();
        result.addAll(due);
        result.addAll(future);
        return result.stream().limit(n).collect(Collectors.toList());
    }

    public List<FlashCard> dueCards(String topicFilter) {
        LocalDateTime now = LocalDateTime.now(UTC);
        Comparator<FlashCard> byDueDate = (a, b) -> {
            boolean aNullReview = a.getNextReview() == null;
            boolean bNullReview = b.getNextReview() == null;
            if (aNullReview && bNullReview) return 0;
            if (aNullReview) return -1;
            if (bNullReview) return 1;
            return a.getNextReview().compareTo(b.getNextReview());
        };
        return loadAllCards().stream()
                .filter(card -> matchesTopic(card, topicFilter))
                .filter(card -> card.getNextReview() == null || !card.getNextReview().isAfter(now))
                .sorted(byDueDate)
                .collect(Collectors.toList());
    }

    public List<FlashCardStats> computeStats() {
        List<FlashCard> allCards = loadAllCards();
        LocalDateTime now = LocalDateTime.now(UTC);
        LocalDate today = LocalDate.now(UTC);

        Map<String, List<FlashCard>> byTopic = allCards.stream()
                .collect(Collectors.groupingBy(
                        FlashCard::getTopic,
                        TreeMap::new,
                        Collectors.toList()));

        return byTopic.entrySet().stream()
                .map(entry -> buildStats(entry.getKey(), entry.getValue(), now, today))
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Review
    // -------------------------------------------------------------------------

    public FlashCard applyReview(FlashCard card, int quality) {
        FlashCard updated = Sm2Algorithm.apply(card, quality);
        saveCard(updated);
        return updated;
    }

    // -------------------------------------------------------------------------
    // Markdown rendering
    // -------------------------------------------------------------------------

    public String renderMarkdown(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        Parser parser = Parser.builder().build();
        HtmlRenderer renderer = HtmlRenderer.builder().build();
        return renderer.render(parser.parse(markdown));
    }

    // -------------------------------------------------------------------------
    // Topic helpers
    // -------------------------------------------------------------------------

    public List<String> allTopics() {
        return loadAllCards().stream()
                .map(FlashCard::getTopic)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Path encoding
    // -------------------------------------------------------------------------

    public String encodePath(String relativePath) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(relativePath.getBytes(StandardCharsets.UTF_8));
    }

    public String decodePath(String encoded) {
        return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    @SneakyThrows
    private FlashCard loadCardFromPath(Path root, Path cardPath) {
        try {
            FlashCard card = createMapper().readValue(cardPath.toFile(), FlashCard.class);
            // forward-slash normalise so Windows paths don't differ from POSIX
            String relative = root.relativize(cardPath).toString().replace('\\', '/');
            card.setRelativePath(relative);
            card.setTopic(topicOf(relative));
            return card;
        } catch (Exception e) {
            log.warn("Skipping unreadable flash card {}: {}", cardPath, e.getMessage());
            return null;
        }
    }

    private static boolean matchesTopic(FlashCard card, String topicFilter) {
        if (topicFilter == null || topicFilter.isBlank()) {
            return true;
        }
        String t = card.getTopic();
        return t.equals(topicFilter) || t.startsWith(topicFilter + "/");
    }

    private static FlashCardStats buildStats(String topic, List<FlashCard> cards,
                                             LocalDateTime now, LocalDate today) {
        int total = cards.size();
        int dueNow = (int) cards.stream()
                .filter(c -> c.getNextReview() == null || !c.getNextReview().isAfter(now))
                .count();
        int reviewedToday = (int) cards.stream()
                .filter(c -> c.getLastReviewed() != null
                             && c.getLastReviewed().toLocalDate().equals(today))
                .count();
        int totalReviews = cards.stream()
                .mapToInt(c -> c.getCorrectCount() + c.getIncorrectCount()).sum();
        int totalCorrect = cards.stream().mapToInt(FlashCard::getCorrectCount).sum();
        double accuracy = totalReviews == 0 ? 0.0 : 100.0 * totalCorrect / totalReviews;
        int streak = computeStreak(cards);
        return new FlashCardStats(topic, total, dueNow, reviewedToday, accuracy, streak);
    }

    /**
     * Streak = count of the most recent consecutive cards (ordered by lastReviewed desc)
     * whose last review was a correct recall.
     *
     * <p>A card's last review was correct when {@code reviewCount > 0}; it was a failure
     * when {@code reviewCount == 0} and the card has been reviewed at least once
     * ({@code correctCount + incorrectCount > 0}).
     */
    private static int computeStreak(List<FlashCard> cards) {
        return (int) cards.stream()
                .filter(c -> c.getLastReviewed() != null)
                .sorted(Comparator.comparing(FlashCard::getLastReviewed).reversed())
                .takeWhile(c -> c.getReviewCount() > 0)
                .count();
    }

    /** Returns the topic portion of a relative path (everything before the last '/'). */
    private static String topicOf(String relativePath) {
        int lastSlash = relativePath.lastIndexOf('/');
        return lastSlash < 0 ? "" : relativePath.substring(0, lastSlash);
    }

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return mapper;
    }
}

