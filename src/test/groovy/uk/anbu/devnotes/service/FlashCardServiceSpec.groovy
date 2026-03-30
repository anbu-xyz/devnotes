package uk.anbu.devnotes.service

import spock.lang.Specification
import spock.lang.TempDir
import uk.anbu.devnotes.types.FlashCard

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

import static java.time.ZoneOffset.UTC

class FlashCardServiceSpec extends Specification {

    @TempDir
    Path tempDir

    ConfigService configService
    FlashCardService service

    def setup() {
        configService = Mock(ConfigService)
        configService.getDocsDirectory() >> tempDir.toString()
        service = new FlashCardService(configService)
    }

    // ---- helpers ----

    /** Write a YAML card file under the flashcards root and return its relative path. */
    private String writeCard(String relPath, String yamlContent) {
        Path root = tempDir.resolve("config/flashcards")
        Path dest = root.resolve(relPath)
        Files.createDirectories(dest.parent)
        Files.writeString(dest, yamlContent)
        return relPath
    }

    private static String minimalCard(String question = "Q?", String answer = "A.") {
        return "question: \"${question}\"\nanswer: \"${answer}\"\n"
    }

    private static String cardWithNextReview(LocalDateTime nextReview) {
        return "question: \"Q\"\nanswer: \"A\"\nnextReview: \"${nextReview}\"\n"
    }

    private static String fullCard(Map args) {
        def sb = new StringBuilder()
        args.each { k, v -> sb << "${k}: \"${v}\"\n" }
        return sb.toString()
    }

    // =========================================================================
    // flashcardsRoot
    // =========================================================================

    def "flashcardsRoot() creates directory and .gitkeep when it does not exist"() {
        when:
        def root = service.flashcardsRoot()

        then:
        Files.isDirectory(root)
        Files.exists(root.resolve(".gitkeep"))
        root == tempDir.resolve("config/flashcards")
    }

    def "flashcardsRoot() is idempotent — calling twice returns same path without error"() {
        when:
        def root1 = service.flashcardsRoot()
        def root2 = service.flashcardsRoot()

        then:
        root1 == root2
        noExceptionThrown()
    }

    // =========================================================================
    // loadAllCards
    // =========================================================================

    def "loadAllCards with nested directories returns correct topic and relativePath"() {
        given:
        writeCard("java/streams/lambda-basics.yaml",  minimalCard("Lambda Q", "Lambda A"))
        writeCard("java/collections/list-methods.yaml", minimalCard("List Q", "List A"))
        writeCard("root-level.yaml", minimalCard("Root Q", "Root A"))

        when:
        def cards = service.loadAllCards()

        then:
        cards.size() == 3

        def lambda = cards.find { it.relativePath == "java/streams/lambda-basics.yaml" }
        lambda != null
        lambda.topic == "java/streams"
        lambda.question == "Lambda Q"

        def list = cards.find { it.relativePath == "java/collections/list-methods.yaml" }
        list != null
        list.topic == "java/collections"

        def root = cards.find { it.relativePath == "root-level.yaml" }
        root != null
        root.topic == ""
    }

    def "card YAML missing SM-2 fields deserialises with Java field defaults"() {
        given:
        writeCard("defaults-test.yaml", "question: \"Q?\"\nanswer: \"A.\"\n")

        when:
        def cards = service.loadAllCards()

        then:
        cards.size() == 1
        with(cards[0]) {
            easeFactor == 2.5d
            interval   == 1
            reviewCount    == 0
            correctCount   == 0
            incorrectCount == 0
            lastReviewed   == null
            nextReview     == null
        }
    }

    def "loadAllCards ignores files starting with a dot"() {
        given:
        writeCard(".hidden.yaml", minimalCard())
        writeCard("visible.yaml", minimalCard())

        when:
        def cards = service.loadAllCards()

        then:
        cards.size() == 1
        cards[0].relativePath == "visible.yaml"
    }

    // =========================================================================
    // loadCard
    // =========================================================================

    def "loadCard returns empty Optional for unknown path"() {
        expect:
        service.loadCard("no-such-file.yaml").isEmpty()
    }

    def "loadCard returns the card when the file exists"() {
        given:
        writeCard("java/basics.yaml", minimalCard("Java Q", "Java A"))

        when:
        def opt = service.loadCard("java/basics.yaml")

        then:
        opt.isPresent()
        opt.get().question == "Java Q"
        opt.get().relativePath == "java/basics.yaml"
        opt.get().topic == "java"
    }

    // =========================================================================
    // saveCard
    // =========================================================================

    def "saveCard round-trip — write then read back preserves all persisted fields"() {
        given:
        def card = new FlashCard()
        card.question      = "What is JVM?"
        card.answer        = "Java Virtual Machine"
        card.reviewCount   = 3
        card.correctCount  = 3
        card.incorrectCount = 0
        card.easeFactor    = 2.6d
        card.interval      = 6
        card.lastReviewed  = LocalDateTime.of(2026, 3, 1, 10, 0)
        card.nextReview    = LocalDateTime.of(2026, 3, 7, 10, 0)
        card.relativePath  = "java/jvm-basics.yaml"
        card.topic         = "java"

        when:
        service.saveCard(card)
        def reloaded = service.loadCard("java/jvm-basics.yaml").get()

        then:
        reloaded.question       == card.question
        reloaded.answer         == card.answer
        reloaded.reviewCount    == card.reviewCount
        reloaded.correctCount   == card.correctCount
        reloaded.incorrectCount == card.incorrectCount
        Math.abs(reloaded.easeFactor - card.easeFactor) < 0.0001d
        reloaded.interval       == card.interval
        reloaded.lastReviewed   == card.lastReviewed
        reloaded.nextReview     == card.nextReview
    }

    def "saveCard does not write relativePath or topic to the YAML file"() {
        given:
        def card = new FlashCard()
        card.question     = "Q"
        card.answer       = "A"
        card.relativePath = "test/card.yaml"
        card.topic        = "test"

        when:
        service.saveCard(card)
        def yamlContent = Files.readString(tempDir.resolve("config/flashcards/test/card.yaml"))

        then:
        !yamlContent.contains("relativePath")
        !yamlContent.contains("topic")
    }

    // =========================================================================
    // dueCards
    // =========================================================================

    def "dueCards(null) returns only cards where nextReview is null or in the past"() {
        given:
        def past   = LocalDateTime.now(UTC).minusDays(1).toString()
        def future = LocalDateTime.now(UTC).plusDays(1).toString()

        writeCard("due-null.yaml",   "question: \"Q\"\nanswer: \"A\"\n")
        writeCard("due-past.yaml",   "question: \"Q\"\nanswer: \"A\"\nnextReview: \"${past}\"\n")
        writeCard("not-due.yaml",    "question: \"Q\"\nanswer: \"A\"\nnextReview: \"${future}\"\n")

        when:
        def due = service.dueCards(null)

        then:
        due.size() == 2
        due*.relativePath.toSet() == ["due-null.yaml", "due-past.yaml"].toSet()
    }

    def "dueCards null nextReview cards are sorted before past nextReview cards"() {
        given:
        def past = LocalDateTime.now(UTC).minusHours(2).toString()
        writeCard("past.yaml", "question: \"Q\"\nanswer: \"A\"\nnextReview: \"${past}\"\n")
        writeCard("null-review.yaml", "question: \"Q\"\nanswer: \"A\"\n")

        when:
        def due = service.dueCards(null)

        then:
        due[0].relativePath == "null-review.yaml"
        due[1].relativePath == "past.yaml"
    }

    def "dueCards with topic filter returns only matching cards"() {
        given:
        def past = LocalDateTime.now(UTC).minusDays(1).toString()
        writeCard("java/streams/card.yaml",      "question: \"Q\"\nanswer: \"A\"\nnextReview: \"${past}\"\n")
        writeCard("java/collections/card.yaml",  "question: \"Q\"\nanswer: \"A\"\nnextReview: \"${past}\"\n")
        writeCard("python/basics/card.yaml",     "question: \"Q\"\nanswer: \"A\"\nnextReview: \"${past}\"\n")

        when:
        def due = service.dueCards("java/streams")

        then:
        due.size() == 1
        due[0].topic == "java/streams"
    }

    def "dueCards with topic filter includes sub-topics"() {
        given:
        def past = LocalDateTime.now(UTC).minusDays(1).toString()
        writeCard("java/streams/card.yaml",         "question: \"Q\"\nanswer: \"A\"\nnextReview: \"${past}\"\n")
        writeCard("java/streams/advanced/card.yaml","question: \"Q\"\nanswer: \"A\"\nnextReview: \"${past}\"\n")
        writeCard("python/card.yaml",               "question: \"Q\"\nanswer: \"A\"\nnextReview: \"${past}\"\n")

        when:
        def due = service.dueCards("java/streams")

        then:
        due.size() == 2
        due.every { it.topic.startsWith("java/streams") }
    }

    // =========================================================================
    // applyReview
    // =========================================================================

    def "applyReview persists updated card and returns the updated card"() {
        given:
        writeCard("review-test.yaml", "question: \"Q\"\nanswer: \"A\"\n")
        def card = service.loadCard("review-test.yaml").get()
        def before = LocalDateTime.now(UTC)

        when:
        def updated = service.applyReview(card, 4)

        then:
        updated.reviewCount == 1
        updated.correctCount == 1
        updated.lastReviewed >= before

        and: "changes were persisted to disk"
        def reloaded = service.loadCard("review-test.yaml").get()
        reloaded.reviewCount == 1
        reloaded.correctCount == 1
    }

    def "applyReview with failed recall persists reset state"() {
        given:
        writeCard("fail-test.yaml",
                "question: \"Q\"\nanswer: \"A\"\nreviewCount: 5\ncorrectCount: 5\ninterval: 30\n")
        def card = service.loadCard("fail-test.yaml").get()

        when:
        service.applyReview(card, 1)

        then:
        def reloaded = service.loadCard("fail-test.yaml").get()
        reloaded.reviewCount   == 0
        reloaded.incorrectCount == 1
        reloaded.interval      == 1
    }

    // =========================================================================
    // allTopics
    // =========================================================================

    def "allTopics returns sorted, distinct list of topic directory paths"() {
        given:
        writeCard("java/streams/card.yaml",      minimalCard())
        writeCard("java/streams/card2.yaml",     minimalCard())
        writeCard("java/collections/card.yaml",  minimalCard())
        writeCard("python/basics/card.yaml",     minimalCard())

        when:
        def topics = service.allTopics()

        then:
        topics == ["java/collections", "java/streams", "python/basics"]
    }

    def "allTopics includes empty string for root-level cards"() {
        given:
        writeCard("root-card.yaml",          minimalCard())
        writeCard("java/card.yaml",          minimalCard())

        when:
        def topics = service.allTopics()

        then:
        topics.contains("")
        topics.contains("java")
        topics == topics.sort()
    }

    // =========================================================================
    // encodePath / decodePath
    // =========================================================================

    def "encodePath then decodePath round-trips the relative path"() {
        given:
        def path = "java/streams/lambda-basics.yaml"

        when:
        def encoded = service.encodePath(path)
        def decoded = service.decodePath(encoded)

        then:
        decoded == path
        !encoded.contains("/")   // URL-safe — no standard base64 slash
        !encoded.contains("+")
        !encoded.contains("=")
    }

    // =========================================================================
    // renderMarkdown
    // =========================================================================

    def "renderMarkdown(**bold**) returns HTML containing <strong>bold</strong>"() {
        when:
        def html = service.renderMarkdown("**bold**")

        then:
        html.contains("<strong>bold</strong>")
    }

    def "renderMarkdown with a fenced code block returns HTML containing a <code> element"() {
        given:
        def md = "```\nint x = 1;\n```"

        when:
        def html = service.renderMarkdown(md)

        then:
        html.contains("<code>")
    }

    def "renderMarkdown plain text returns HTML containing the text"() {
        when:
        def html = service.renderMarkdown("plain text")

        then:
        html.contains("plain text")
    }

    def "renderMarkdown null returns empty string"() {
        expect:
        service.renderMarkdown(null) == ""
    }

    def "renderMarkdown blank string returns empty string"() {
        expect:
        service.renderMarkdown("   ") == ""
    }
}

