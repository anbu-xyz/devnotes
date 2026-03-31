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

    // =========================================================================
    // computeStats — reviewedToday
    // =========================================================================

    def "computeStats reviewedToday counts only cards whose lastReviewed is today"() {
        given:
        def nowStr       = LocalDateTime.now(UTC).toString()
        def yesterdayStr = LocalDateTime.now(UTC).minusDays(1).toString()

        writeCard("topic/card-today.yaml", """\
question: "Q1"
answer: "A1"
lastReviewed: "${nowStr}"
reviewCount: 1
correctCount: 1
""")
        writeCard("topic/card-yesterday.yaml", """\
question: "Q2"
answer: "A2"
lastReviewed: "${yesterdayStr}"
reviewCount: 1
correctCount: 1
""")
        writeCard("topic/card-never.yaml", "question: \"Q3\"\nanswer: \"A3\"\n")

        when:
        def stats = service.computeStats()

        then:
        stats.size() == 1
        stats[0].reviewedToday == 1
    }

    def "computeStats reviewedToday is zero when no cards were reviewed today"() {
        given:
        def yesterdayStr = LocalDateTime.now(UTC).minusDays(1).toString()
        writeCard("topic/old.yaml", """\
question: "Q"
answer: "A"
lastReviewed: "${yesterdayStr}"
reviewCount: 1
correctCount: 1
""")

        when:
        def stats = service.computeStats()

        then:
        stats[0].reviewedToday == 0
    }

    // =========================================================================
    // computeStats — streak
    // =========================================================================

    def "computeStats streak counts all consecutive correct cards ordered newest-first"() {
        given: "three cards, all last-reviewed correctly (reviewCount > 0)"
        def t1 = LocalDateTime.now(UTC).minusHours(1).toString()
        def t2 = LocalDateTime.now(UTC).minusHours(2).toString()
        def t3 = LocalDateTime.now(UTC).minusHours(3).toString()

        writeCard("topic/card1.yaml", """\
question: "Q1"
answer: "A1"
lastReviewed: "${t1}"
reviewCount: 2
correctCount: 2
""")
        writeCard("topic/card2.yaml", """\
question: "Q2"
answer: "A2"
lastReviewed: "${t2}"
reviewCount: 3
correctCount: 3
""")
        writeCard("topic/card3.yaml", """\
question: "Q3"
answer: "A3"
lastReviewed: "${t3}"
reviewCount: 1
correctCount: 1
""")

        when:
        def stats = service.computeStats()

        then:
        stats[0].currentStreak == 3
    }

    def "computeStats streak breaks when a card has reviewCount=0 (failed last review)"() {
        given: "card1 newest (correct), card2 middle (failed, reviewCount reset to 0), card3 oldest (correct)"
        def t1 = LocalDateTime.now(UTC).minusHours(1).toString()
        def t2 = LocalDateTime.now(UTC).minusHours(2).toString()
        def t3 = LocalDateTime.now(UTC).minusHours(3).toString()

        writeCard("topic/card1.yaml", """\
question: "Q1"
answer: "A1"
lastReviewed: "${t1}"
reviewCount: 1
correctCount: 3
""")
        writeCard("topic/card2.yaml", """\
question: "Q2"
answer: "A2"
lastReviewed: "${t2}"
reviewCount: 0
correctCount: 2
incorrectCount: 1
""")
        writeCard("topic/card3.yaml", """\
question: "Q3"
answer: "A3"
lastReviewed: "${t3}"
reviewCount: 2
correctCount: 2
""")

        when:
        def stats = service.computeStats()

        then:
        stats[0].currentStreak == 1  // only card1; card2 breaks the streak
    }

    def "computeStats streak is zero when no cards have been reviewed"() {
        given:
        writeCard("topic/unreviewed.yaml", "question: \"Q\"\nanswer: \"A\"\n")

        when:
        def stats = service.computeStats()

        then:
        stats[0].currentStreak == 0
    }

    def "computeStats streak excludes cards from a different topic"() {
        given: "two topics each with one correct card"
        def t1 = LocalDateTime.now(UTC).minusHours(1).toString()
        def t2 = LocalDateTime.now(UTC).minusHours(2).toString()

        writeCard("java/card.yaml", """\
question: "Q1"
answer: "A1"
lastReviewed: "${t1}"
reviewCount: 1
correctCount: 1
""")
        writeCard("python/card.yaml", """\
question: "Q2"
answer: "A2"
lastReviewed: "${t2}"
reviewCount: 1
correctCount: 1
""")

        when:
        def stats = service.computeStats()
        def javaStats  = stats.find { it.topic == "java" }
        def pythonStats = stats.find { it.topic == "python" }

        then: "each topic's streak is computed independently from its own cards"
        javaStats.currentStreak  == 1
        pythonStats.currentStreak == 1
    }

    // =========================================================================
    // pickRandomCards
    // =========================================================================

    def "pickRandomCards returns empty list when n=0"() {
        given:
        writeCard("java/card.yaml", minimalCard())

        expect:
        service.pickRandomCards(null, 0).isEmpty()
    }

    def "pickRandomCards returns empty list when n is negative"() {
        given:
        writeCard("java/card.yaml", minimalCard())

        expect:
        service.pickRandomCards(null, -1).isEmpty()
    }

    def "pickRandomCards returns all cards when n exceeds total"() {
        given:
        writeCard("a.yaml", minimalCard("Q1", "A1"))
        writeCard("b.yaml", minimalCard("Q2", "A2"))

        when:
        def result = service.pickRandomCards(null, 100)

        then:
        result.size() == 2
    }

    def "pickRandomCards returns exactly n cards when more cards exist"() {
        given:
        (1..8).each { i -> writeCard("card${i}.yaml", minimalCard("Q${i}", "A${i}")) }

        when:
        def result = service.pickRandomCards(null, 3)

        then:
        result.size() == 3
    }

    def "pickRandomCards places due cards before future cards"() {
        given:
        def future = LocalDateTime.now(UTC).plusDays(7).toString()
        writeCard("future.yaml",  "question: \"F\"\nanswer: \"A\"\nnextReview: \"${future}\"\n")
        writeCard("due-null.yaml", minimalCard("DueNull", "A"))

        when:
        def result = service.pickRandomCards(null, 2)

        then:
        result.size() == 2
        result[0].relativePath == "due-null.yaml"
        result[1].relativePath == "future.yaml"
    }

    def "pickRandomCards orders future cards by nextReview ascending"() {
        given:
        def soon   = LocalDateTime.now(UTC).plusDays(1).toString()
        def later  = LocalDateTime.now(UTC).plusDays(5).toString()
        def latest = LocalDateTime.now(UTC).plusDays(10).toString()
        writeCard("latest.yaml", "question: \"Q\"\nanswer: \"A\"\nnextReview: \"${latest}\"\n")
        writeCard("soon.yaml",   "question: \"Q\"\nanswer: \"A\"\nnextReview: \"${soon}\"\n")
        writeCard("later.yaml",  "question: \"Q\"\nanswer: \"A\"\nnextReview: \"${later}\"\n")

        when:
        def result = service.pickRandomCards(null, 3)

        then:
        result*.relativePath == ["soon.yaml", "later.yaml", "latest.yaml"]
    }

    def "pickRandomCards respects topic filter"() {
        given:
        def future = LocalDateTime.now(UTC).plusDays(7).toString()
        writeCard("java/card.yaml",   minimalCard("Java Q", "A"))
        writeCard("python/card.yaml", minimalCard("Python Q", "A"))

        when:
        def result = service.pickRandomCards("java", 10)

        then:
        result.size() == 1
        result[0].topic == "java"
    }

    def "pickRandomCards includes sub-topics when filtering by parent"() {
        given:
        writeCard("java/streams/card.yaml",          minimalCard("Q1", "A"))
        writeCard("java/streams/advanced/card.yaml", minimalCard("Q2", "A"))
        writeCard("python/card.yaml",                minimalCard("Q3", "A"))

        when:
        def result = service.pickRandomCards("java/streams", 10)

        then:
        result.size() == 2
        result.every { it.topic.startsWith("java/streams") }
    }

    def "pickRandomCards with no cards returns empty list"() {
        expect:
        service.pickRandomCards(null, 5).isEmpty()
    }

    def "pickRandomCards due cards are all present when n equals due count"() {
        given:
        def past = LocalDateTime.now(UTC).minusDays(1).toString()
        writeCard("due1.yaml", "question: \"Q\"\nanswer: \"A\"\nnextReview: \"${past}\"\n")
        writeCard("due2.yaml", "question: \"Q\"\nanswer: \"A\"\nnextReview: \"${past}\"\n")
        def future = LocalDateTime.now(UTC).plusDays(3).toString()
        writeCard("future.yaml", "question: \"Q\"\nanswer: \"A\"\nnextReview: \"${future}\"\n")

        when:
        def result = service.pickRandomCards(null, 2)

        then:
        result.size() == 2
        result.every { it.getNextReview() == null || !it.getNextReview().isAfter(LocalDateTime.now(UTC)) }
    }
}

