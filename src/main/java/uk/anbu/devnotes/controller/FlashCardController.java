package uk.anbu.devnotes.controller;

import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devnotes.service.FlashCardService;
import uk.anbu.devnotes.types.FlashCard;
import uk.anbu.devnotes.types.FlashCardStats;

import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.time.ZoneOffset.UTC;

@RestController
@RequiredArgsConstructor
@Slf4j
public class FlashCardController {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final String SESSION_RANDOM_QUEUE = "FC_RANDOM_QUEUE";
    private static final String SESSION_RANDOM_TOTAL = "FC_RANDOM_TOTAL";

    private final FlashCardService service;
    private final TemplateEngine templateEngine;

    // -------------------------------------------------------------------------
    // GET /flashcards — summary page
    // -------------------------------------------------------------------------

    @GetMapping("/flashcards")
    public ResponseEntity<String> summary() {
        List<FlashCard> allCards = service.loadAllCards();
        List<FlashCardStats> topicStats = service.computeStats();

        LocalDateTime now = LocalDateTime.now(UTC);
        LocalDate today = LocalDate.now(UTC);

        int totalCards = allCards.size();
        int totalDue = (int) allCards.stream()
                .filter(c -> c.getNextReview() == null || !c.getNextReview().isAfter(now))
                .count();
        int reviewedToday = (int) allCards.stream()
                .filter(c -> c.getLastReviewed() != null
                             && c.getLastReviewed().toLocalDate().equals(today))
                .count();
        int totalReviews = allCards.stream()
                .mapToInt(c -> c.getCorrectCount() + c.getIncorrectCount()).sum();
        int totalCorrect = allCards.stream().mapToInt(FlashCard::getCorrectCount).sum();
        double globalAccuracy = totalReviews == 0 ? 0.0 : 100.0 * totalCorrect / totalReviews;

        var params = new HashMap<String, Object>();
        params.put("topicStats", topicStats);
        params.put("totalCards", totalCards);
        params.put("totalDue", totalDue);
        params.put("reviewedToday", reviewedToday);
        params.put("globalAccuracy", globalAccuracy);

        return render("flashcards/summary.jte", params);
    }

    // -------------------------------------------------------------------------
    // GET /flashcards/review — show next due card
    // -------------------------------------------------------------------------

    @GetMapping("/flashcards/review")
    public ResponseEntity<String> reviewGet(
            @RequestParam(required = false, defaultValue = "") String topic) {

        List<FlashCard> due = service.dueCards(topic);
        var params = new HashMap<String, Object>();
        params.put("topic", topic);

        if (due.isEmpty()) {
            String nextDueAt = service.loadAllCards().stream()
                    .filter(c -> c.getNextReview() != null
                                 && c.getNextReview().isAfter(LocalDateTime.now(UTC)))
                    .min(Comparator.comparing(FlashCard::getNextReview))
                    .map(c -> c.getNextReview().format(DISPLAY_FMT))
                    .orElse(null);
            params.put("card", null);
            params.put("questionHtml", null);
            params.put("answerHtml", null);
            params.put("encodedPath", null);
            params.put("remaining", 0);
            params.put("nextDueAt", nextDueAt);
        } else {
            FlashCard card = due.get(0);
            params.put("card", card);
            params.put("questionHtml", service.renderMarkdown(card.getQuestion()));
            params.put("answerHtml", service.renderMarkdown(card.getAnswer()));
            params.put("encodedPath", service.encodePath(card.getRelativePath()));
            params.put("remaining", due.size() - 1);
            params.put("nextDueAt", null);
        }

        return render("flashcards/review.jte", params);
    }

    // -------------------------------------------------------------------------
    // POST /flashcards/review — submit rating
    // -------------------------------------------------------------------------

    @PostMapping("/flashcards/review")
    public ResponseEntity<String> reviewPost(
            @RequestParam String relativePath,
            @RequestParam int quality,
            @RequestParam(required = false, defaultValue = "") String topic) {

        if (quality < 0 || quality > 5) {
            return ResponseEntity.badRequest()
                    .body("Quality must be between 0 and 5, got: " + quality);
        }
        Optional<FlashCard> cardOpt = service.loadCard(relativePath);
        if (cardOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Card not found: " + relativePath);
        }
        service.applyReview(cardOpt.get(), quality);

        String location = "/flashcards/review" + (topic.isBlank() ? "" : "?topic=" + topic);
        return redirect(location);
    }

    // -------------------------------------------------------------------------
    // GET /flashcards/review/random — start or continue a random-N session
    // -------------------------------------------------------------------------

    @GetMapping("/flashcards/review/random")
    public ResponseEntity<String> randomReviewGet(
            @RequestParam(required = false) Integer n,
            @RequestParam(required = false, defaultValue = "") String topic,
            HttpSession session) {

        if (n != null) {
            // Start a fresh session: pick cards and store the queue
            List<FlashCard> picked = service.pickRandomCards(topic, n);
            List<String> queue = new ArrayList<>(picked.stream()
                    .map(FlashCard::getRelativePath)
                    .toList());
            session.setAttribute(SESSION_RANDOM_QUEUE, queue);
            session.setAttribute(SESSION_RANDOM_TOTAL, queue.size());
            return redirect("/flashcards/review/random");
        }

        @SuppressWarnings("unchecked")
        List<String> queue = (List<String>) session.getAttribute(SESSION_RANDOM_QUEUE);
        Object totalObj = session.getAttribute(SESSION_RANDOM_TOTAL);
        int total = totalObj instanceof Integer t ? t : 0;

        var params = new HashMap<String, Object>();
        params.put("sessionTotal", total);

        if (queue == null || queue.isEmpty()) {
            params.put("card", null);
            params.put("questionHtml", null);
            params.put("answerHtml", null);
            params.put("encodedPath", null);
            params.put("remaining", 0);
            return render("flashcards/review-random.jte", params);
        }

        // Skip deleted cards gracefully
        while (!queue.isEmpty()) {
            String nextPath = queue.get(0);
            Optional<FlashCard> cardOpt = service.loadCard(nextPath);
            if (cardOpt.isPresent()) {
                FlashCard card = cardOpt.get();
                params.put("card", card);
                params.put("questionHtml", service.renderMarkdown(card.getQuestion()));
                params.put("answerHtml", service.renderMarkdown(card.getAnswer()));
                params.put("encodedPath", service.encodePath(card.getRelativePath()));
                params.put("remaining", queue.size() - 1);
                return render("flashcards/review-random.jte", params);
            }
            queue.remove(0);
        }

        // All remaining cards were missing
        params.put("card", null);
        params.put("questionHtml", null);
        params.put("answerHtml", null);
        params.put("encodedPath", null);
        params.put("remaining", 0);
        return render("flashcards/review-random.jte", params);
    }

    // -------------------------------------------------------------------------
    // POST /flashcards/review/random — submit rating for a random-session card
    // -------------------------------------------------------------------------

    @PostMapping("/flashcards/review/random")
    public ResponseEntity<String> randomReviewPost(
            @RequestParam String relativePath,
            @RequestParam int quality,
            HttpSession session) {

        if (quality < 0 || quality > 5) {
            return ResponseEntity.badRequest()
                    .body("Quality must be between 0 and 5, got: " + quality);
        }
        service.loadCard(relativePath).ifPresent(card -> service.applyReview(card, quality));

        @SuppressWarnings("unchecked")
        List<String> queue = (List<String>) session.getAttribute(SESSION_RANDOM_QUEUE);
        if (queue != null && !queue.isEmpty()) {
            queue.remove(0);
        }
        return redirect("/flashcards/review/random");
    }

    // -------------------------------------------------------------------------
    // GET /flashcards/new — blank form
    // -------------------------------------------------------------------------

    @GetMapping("/flashcards/new")
    public ResponseEntity<String> newCardGet(
            @RequestParam(required = false, defaultValue = "") String topic) {

        var params = new HashMap<String, Object>();
        params.put("topics", service.allTopics());
        params.put("selectedTopic", topic);
        params.put("error", null);
        return render("flashcards/new-card.jte", params);
    }

    // -------------------------------------------------------------------------
    // POST /flashcards/new — create card
    // -------------------------------------------------------------------------

    @PostMapping("/flashcards/new")
    public ResponseEntity<String> newCardPost(
            @RequestParam(required = false, defaultValue = "") String topic,
            @RequestParam(required = false, defaultValue = "") String question,
            @RequestParam(required = false, defaultValue = "") String answer) {

        if (question.isBlank()) {
            return renderNewCardForm(topic, "Question cannot be blank.");
        }
        if (answer.isBlank()) {
            return renderNewCardForm(topic, "Answer cannot be blank.");
        }

        try {
            String filename = slugify(question);
            String relPath = topic.isBlank() ? filename : topic + "/" + filename;

            // Avoid overwriting an existing file
            var root = service.flashcardsRoot();
            int counter = 2;
            while (Files.exists(root.resolve(relPath))) {
                String base = filename.substring(0, filename.length() - 5);
                String candidate = base + "-" + counter + ".yaml";
                relPath = topic.isBlank() ? candidate : topic + "/" + candidate;
                counter++;
            }

            FlashCard card = new FlashCard();
            card.setQuestion(question);
            card.setAnswer(answer);
            card.setRelativePath(relPath);
            card.setTopic(topic);
            service.saveCard(card);

            return redirect("/flashcards");
        } catch (Exception e) {
            log.error("Error saving new flash card", e);
            return renderNewCardForm(topic, "Error saving card: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // GET /flashcards/edit/{encodedPath} — pre-filled edit form
    // -------------------------------------------------------------------------

    @GetMapping("/flashcards/edit/{encodedPath}")
    public ResponseEntity<String> editCardGet(@PathVariable String encodedPath) {
        String relativePath;
        try {
            relativePath = service.decodePath(encodedPath);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid encoded path: " + encodedPath);
        }

        Optional<FlashCard> cardOpt = service.loadCard(relativePath);
        if (cardOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Card not found: " + relativePath);
        }

        return renderEditCardForm(cardOpt.get(), encodedPath, null);
    }

    // -------------------------------------------------------------------------
    // POST /flashcards/edit/{encodedPath} — save edits
    // -------------------------------------------------------------------------

    @PostMapping("/flashcards/edit/{encodedPath}")
    public ResponseEntity<String> editCardPost(
            @PathVariable String encodedPath,
            @RequestParam(required = false, defaultValue = "") String question,
            @RequestParam(required = false, defaultValue = "") String answer) {

        String relativePath;
        try {
            relativePath = service.decodePath(encodedPath);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid encoded path: " + encodedPath);
        }

        Optional<FlashCard> cardOpt = service.loadCard(relativePath);
        if (cardOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Card not found: " + relativePath);
        }

        if (question.isBlank()) {
            return renderEditCardForm(cardOpt.get(), encodedPath, "Question cannot be blank.");
        }
        if (answer.isBlank()) {
            return renderEditCardForm(cardOpt.get(), encodedPath, "Answer cannot be blank.");
        }

        FlashCard card = cardOpt.get();
        card.setQuestion(question);
        card.setAnswer(answer);
        service.saveCard(card);

        return redirect("/flashcards");
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private ResponseEntity<String> render(String template, Map<String, Object> params) {
        TemplateOutput output = new StringOutput();
        templateEngine.render(template, params, output);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(output.toString());
    }

    private ResponseEntity<String> renderNewCardForm(String topic, String error) {
        var params = new HashMap<String, Object>();
        params.put("topics", service.allTopics());
        params.put("selectedTopic", topic);
        params.put("error", error);
        TemplateOutput output = new StringOutput();
        templateEngine.render("flashcards/new-card.jte", params, output);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.TEXT_HTML)
                .body(output.toString());
    }

    private ResponseEntity<String> renderEditCardForm(FlashCard card, String encodedPath,
                                                      String error) {
        var params = new HashMap<String, Object>();
        params.put("card", card);
        params.put("encodedPath", encodedPath);
        params.put("error", error);
        TemplateOutput output = new StringOutput();
        templateEngine.render("flashcards/edit-card.jte", params, output);
        return ResponseEntity.status(error == null ? HttpStatus.OK : HttpStatus.BAD_REQUEST)
                .contentType(MediaType.TEXT_HTML)
                .body(output.toString());
    }

    private static ResponseEntity<String> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location)
                .build();
    }

    /**
     * Converts the first 40 characters of a question into a safe filename stem:
     * lowercase, spaces→hyphens, strip non-alphanumeric/hyphen chars.
     */
    static String slugify(String question) {
        String s = question.toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        if (s.length() > 40) {
            s = s.substring(0, 40).replaceAll("-$", "");
        }
        if (s.isEmpty()) {
            s = "card";
        }
        return s + ".yaml";
    }
}

