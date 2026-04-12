package uk.anbu.devnotes.controller

import gg.jte.ContentType
import gg.jte.TemplateEngine
import gg.jte.resolve.DirectoryCodeResolver
import org.jsoup.Jsoup
import spock.lang.Specification
import spock.lang.TempDir
import org.springframework.mock.web.MockHttpSession
import uk.anbu.devnotes.service.ConfigService
import uk.anbu.devnotes.service.FlashCardService

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime

import static java.time.ZoneOffset.UTC

class FlashCardControllerSpec extends Specification {

    @TempDir
    Path tempDir

    FlashCardController controller
    FlashCardService service

    def setup() {
        ConfigService configService = Mock(ConfigService)
        configService.getDocsDirectory() >> tempDir.toString()

        service = new FlashCardService(configService)

        def codeResolver = new DirectoryCodeResolver(Paths.get("src/main/jte"))
        def te = TemplateEngine.create(codeResolver, Paths.get("src/main/jte"), ContentType.Html)

        controller = new FlashCardController(service, te)
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String writeCard(String relPath, String yaml) {
        Path root = tempDir.resolve("config/flashcards")
        Path dest = root.resolve(relPath)
        Files.createDirectories(dest.parent)
        Files.writeString(dest, yaml)
        return relPath
    }

    private static String minimalYaml(String question, String answer) {
        "question: \"${question}\"\nanswer: \"${answer}\"\n"
    }

    private static String pastDueYaml(String question, String answer) {
        def past = LocalDateTime.now(UTC).minusDays(1)
        "question: \"${question}\"\nanswer: \"${answer}\"\nnextReview: \"${past}\"\n"
    }

    private static String futureDueYaml(String question, String answer) {
        def future = LocalDateTime.now(UTC).plusDays(7)
        "question: \"${question}\"\nanswer: \"${answer}\"\nnextReview: \"${future}\"\n"
    }

    // =========================================================================
    // GET /flashcards - summary page
    // =========================================================================

    def "GET /flashcards returns 200 with Flash Cards heading"() {
        when:
        def response = controller.summary()

        then:
        response.statusCode.value() == 200
        response.body.contains("Flash Cards")
    }

    def "GET /flashcards shows correct total card count"() {
        given:
        writeCard("java/card1.yaml", minimalYaml("Q1", "A1"))
        writeCard("java/card2.yaml", minimalYaml("Q2", "A2"))

        when:
        def response = controller.summary()
        def doc = Jsoup.parse(response.body)

        then:
        // totalCards = 2 is rendered in the stats bar
        response.body.contains("2")
    }

    // =========================================================================
    // GET /flashcards/review - with a due card
    // =========================================================================

    def "GET /flashcards/review with a due card returns 200 containing the question text"() {
        given:
        writeCard("test/card.yaml", minimalYaml("What is Java?", "A language"))

        when:
        def response = controller.reviewGet("")

        then:
        response.statusCode.value() == 200
        response.body.contains("What is Java?")
    }

    def "GET /flashcards/review renders markdown in the question as HTML"() {
        given:
        writeCard("test/bold.yaml", minimalYaml("What is **bold** text?", "Bold answer"))

        when:
        def response = controller.reviewGet("")

        then:
        response.body.contains("<strong>bold</strong>")
    }

    def "GET /flashcards/review renders markdown in the answer as HTML"() {
        given:
        writeCard("test/code.yaml", minimalYaml("Question?", "Use `System.out.println()`"))

        when:
        def response = controller.reviewGet("")

        then:
        response.body.contains("<code>System.out.println()</code>")
    }

    def "GET /flashcards/review with no due cards returns 200 with all-caught-up message"() {
        given:
        writeCard("test/future.yaml", futureDueYaml("Not due yet", "Answer"))

        when:
        def response = controller.reviewGet("")

        then:
        response.statusCode.value() == 200
        response.body.contains("All caught up")
    }

    def "GET /flashcards/review with no cards at all returns all-caught-up page"() {
        when:
        def response = controller.reviewGet("")

        then:
        response.statusCode.value() == 200
        response.body.contains("All caught up")
    }

    def "GET /flashcards/review?topic filter returns only matching topic card"() {
        given:
        writeCard("java/streams/q.yaml", pastDueYaml("Java streams question", "Java answer"))
        writeCard("python/basics/q.yaml", pastDueYaml("Python question", "Python answer"))

        when:
        def response = controller.reviewGet("java/streams")

        then:
        response.body.contains("Java streams question")
        !response.body.contains("Python question")
    }

    def "GET /flashcards/review shows remaining count"() {
        given:
        writeCard("test/card1.yaml", minimalYaml("Q1", "A1"))
        writeCard("test/card2.yaml", minimalYaml("Q2", "A2"))
        writeCard("test/card3.yaml", minimalYaml("Q3", "A3"))

        when:
        def response = controller.reviewGet("")

        then:
        // 3 due cards → remaining after first = 2, displayed as "3 card(s) remaining"
        response.body.contains("3 card(s) remaining")
    }

    // =========================================================================
    // POST /flashcards/review - submit rating
    // =========================================================================

    def "POST /flashcards/review with valid quality returns 302 redirect"() {
        given:
        writeCard("test/card.yaml", minimalYaml("Q?", "A."))

        when:
        def response = controller.reviewPost("test/card.yaml", 4, "")

        then:
        response.statusCode.value() == 302
        response.headers.getFirst("Location") == "/flashcards/review"
    }

    def "POST /flashcards/review with topic preserves topic in redirect"() {
        given:
        writeCard("java/card.yaml", minimalYaml("Q?", "A."))

        when:
        def response = controller.reviewPost("java/card.yaml", 3, "java")

        then:
        response.statusCode.value() == 302
        response.headers.getFirst("Location") == "/flashcards/review?topic=java"
    }

    def "POST /flashcards/review with quality=6 returns 400"() {
        when:
        def response = controller.reviewPost("any/path.yaml", 6, "")

        then:
        response.statusCode.value() == 400
    }

    def "POST /flashcards/review with quality=-1 returns 400"() {
        when:
        def response = controller.reviewPost("any/path.yaml", -1, "")

        then:
        response.statusCode.value() == 400
    }

    def "POST /flashcards/review updates card SM-2 data on disk"() {
        given:
        writeCard("test/reviewable.yaml", minimalYaml("Q?", "A."))

        when:
        controller.reviewPost("test/reviewable.yaml", 5, "")
        def card = service.loadCard("test/reviewable.yaml").get()

        then:
        card.reviewCount == 1
        card.correctCount == 1
        card.lastReviewed != null
    }

    // =========================================================================
    // GET /flashcards/new
    // =========================================================================

    def "GET /flashcards/new returns 200 with form HTML"() {
        when:
        def response = controller.newCardGet("")

        then:
        response.statusCode.value() == 200
        def doc = Jsoup.parse(response.body)
        doc.select("textarea[name=question]").size() == 1
        doc.select("textarea[name=answer]").size() == 1
    }

    def "GET /flashcards/new?topic pre-fills the topic input"() {
        when:
        def response = controller.newCardGet("java/streams")

        then:
        response.statusCode.value() == 200
        response.body.contains("java/streams")
    }

    def "GET /flashcards/new shows existing topics in datalist"() {
        given:
        writeCard("java/streams/card.yaml", minimalYaml("Q", "A"))

        when:
        def response = controller.newCardGet("")

        then:
        response.body.contains("java/streams")
    }

    // =========================================================================
    // POST /flashcards/new
    // =========================================================================

    def "POST /flashcards/new with valid data returns 302 and creates YAML file"() {
        when:
        def response = controller.newCardPost("java/test", "What is a JVM?", "Java Virtual Machine")

        then:
        response.statusCode.value() == 302
        response.headers.getFirst("Location") == "/flashcards"

        and: "YAML file was created under the flashcards root"
        def root = tempDir.resolve("config/flashcards/java/test")
        Files.list(root).count() == 1
        def yaml = Files.list(root).findFirst().get()
        yaml.fileName.toString().endsWith(".yaml")
        Files.readString(yaml).contains("What is a JVM?")
    }

    def "POST /flashcards/new with blank question returns 400 with error message"() {
        when:
        def response = controller.newCardPost("java", "", "Some answer")

        then:
        response.statusCode.value() == 400
        response.body.contains("blank")
    }

    def "POST /flashcards/new with blank answer returns 400 with error message"() {
        when:
        def response = controller.newCardPost("java", "Some question?", "")

        then:
        response.statusCode.value() == 400
        response.body.contains("blank")
    }

    def "POST /flashcards/new deduplicates filename if file already exists"() {
        given: "a card with the same slug already exists"
        writeCard("java/what-is-a-jvm.yaml", minimalYaml("What is a JVM?", "Old answer"))

        when:
        def response = controller.newCardPost("java", "What is a JVM?", "New answer")

        then:
        response.statusCode.value() == 302
        def root = tempDir.resolve("config/flashcards/java")
        Files.list(root).count() == 2
    }

    // =========================================================================
    // GET /flashcards/edit/{encodedPath}
    // =========================================================================

    def "GET /flashcards/edit/{encodedPath} returns 200 with pre-filled question and answer"() {
        given:
        writeCard("java/edit-test.yaml", minimalYaml("Edit this question?", "Edit this answer."))
        def encoded = service.encodePath("java/edit-test.yaml")

        when:
        def response = controller.editCardGet(encoded)

        then:
        response.statusCode.value() == 200
        response.body.contains("Edit this question?")
        response.body.contains("Edit this answer.")
    }

    def "GET /flashcards/edit/{encodedPath} returns 404 for non-existent card"() {
        given:
        def encoded = service.encodePath("java/no-such-card.yaml")

        when:
        def response = controller.editCardGet(encoded)

        then:
        response.statusCode.value() == 404
    }

    def "GET /flashcards/edit with invalid base64 returns 400"() {
        when:
        def response = controller.editCardGet("not!!valid!!base64")

        then:
        response.statusCode.value() == 400
    }

    // =========================================================================
    // POST /flashcards/edit/{encodedPath}
    // =========================================================================

    def "POST /flashcards/edit/{encodedPath} updates question and answer, preserves SM-2 metadata"() {
        given: "a card with existing SM-2 data"
        writeCard("java/meta-test.yaml", """
question: "Original question"
answer: "Original answer"
reviewCount: 4
correctCount: 3
incorrectCount: 1
easeFactor: 2.36
interval: 6
""")
        def encoded = service.encodePath("java/meta-test.yaml")

        when:
        def response = controller.editCardPost(encoded, "Updated question?", "Updated answer.")

        then:
        response.statusCode.value() == 302
        response.headers.getFirst("Location") == "/flashcards"

        and: "question and answer were updated"
        def reloaded = service.loadCard("java/meta-test.yaml").get()
        reloaded.question == "Updated question?"
        reloaded.answer == "Updated answer."

        and: "SM-2 metadata was preserved"
        reloaded.reviewCount    == 4
        reloaded.correctCount   == 3
        reloaded.incorrectCount == 1
        Math.abs(reloaded.easeFactor - 2.36d) < 0.001d
        reloaded.interval       == 6
    }

    def "POST /flashcards/edit with blank question returns 400"() {
        given:
        writeCard("java/blank-q.yaml", minimalYaml("Q?", "A."))
        def encoded = service.encodePath("java/blank-q.yaml")

        when:
        def response = controller.editCardPost(encoded, "", "Some answer")

        then:
        response.statusCode.value() == 400
        response.body.contains("blank")
    }

    def "POST /flashcards/edit with invalid base64 returns 400"() {
        when:
        def response = controller.editCardPost("not!!valid!!base64", "Q?", "A.")

        then:
        response.statusCode.value() == 400
    }

    // =========================================================================
    // slugify helper
    // =========================================================================

    def "slugify converts question to a safe filename"() {
        expect:
        FlashCardController.slugify(input) == expected

        where:
        input                         | expected
        "What is Java?"               | "what-is-java.yaml"
        "  spaces   everywhere  "     | "spaces-everywhere.yaml"
        "C++ and @annotations!"       | "c-and-annotations.yaml"
        ""                            | "card.yaml"
        "a" * 50                      | "a" * 40 + ".yaml"
    }

    // =========================================================================
    // GET /flashcards/review/random - start session
    // =========================================================================

    def "GET /flashcards/review/random?n=N redirects to /flashcards/review/random and stores queue in session"() {
        given:
        writeCard("java/card1.yaml", minimalYaml("Q1", "A1"))
        writeCard("java/card2.yaml", minimalYaml("Q2", "A2"))
        def session = new MockHttpSession()

        when:
        def response = controller.randomReviewGet(2, "", session)

        then:
        response.statusCode.value() == 302
        response.headers.getFirst("Location") == "/flashcards/review/random"
        (session.getAttribute("FC_RANDOM_QUEUE") as List).size() == 2
        session.getAttribute("FC_RANDOM_TOTAL") == 2
    }

    def "GET /flashcards/review/random without n shows first card from session queue"() {
        given:
        writeCard("java/card.yaml", minimalYaml("What is encapsulation?", "Hiding internal state"))
        def session = new MockHttpSession()
        // Start the session first
        controller.randomReviewGet(1, "", session)

        when:
        def response = controller.randomReviewGet(null, "", session)

        then:
        response.statusCode.value() == 200
        response.body.contains("What is encapsulation?")
        response.body.contains("Random Review")
    }

    def "GET /flashcards/review/random without session shows session-complete page"() {
        given:
        def session = new MockHttpSession()

        when:
        def response = controller.randomReviewGet(null, "", session)

        then:
        response.statusCode.value() == 200
        response.body.contains("Session complete")
    }

    def "GET /flashcards/review/random with empty queue shows session-complete page"() {
        given:
        def session = new MockHttpSession()
        session.setAttribute("FC_RANDOM_QUEUE", new ArrayList<String>())
        session.setAttribute("FC_RANDOM_TOTAL", 3)

        when:
        def response = controller.randomReviewGet(null, "", session)

        then:
        response.statusCode.value() == 200
        response.body.contains("Session complete")
        response.body.contains("3")   // session total shown
    }

    def "GET /flashcards/review/random?n limits to n cards even when more exist"() {
        given:
        (1..10).each { i -> writeCard("card${i}.yaml", minimalYaml("Q${i}", "A${i}")) }
        def session = new MockHttpSession()

        when:
        controller.randomReviewGet(4, "", session)

        then:
        (session.getAttribute("FC_RANDOM_QUEUE") as List).size() == 4
        session.getAttribute("FC_RANDOM_TOTAL") == 4
    }

    def "GET /flashcards/review/random?n=5 with topic filter stores only matching cards"() {
        given:
        writeCard("java/card.yaml",   minimalYaml("Java Q", "Java A"))
        writeCard("python/card.yaml", minimalYaml("Python Q", "Python A"))
        def session = new MockHttpSession()

        when:
        controller.randomReviewGet(10, "java", session)

        then:
        def queue = session.getAttribute("FC_RANDOM_QUEUE") as List<String>
        queue.size() == 1
        queue[0].startsWith("java/")
    }

    def "GET /flashcards/review/random shows progress counter"() {
        given:
        writeCard("a.yaml", minimalYaml("Q1", "A1"))
        writeCard("b.yaml", minimalYaml("Q2", "A2"))
        writeCard("c.yaml", minimalYaml("Q3", "A3"))
        def session = new MockHttpSession()
        controller.randomReviewGet(3, "", session)

        when:
        def response = controller.randomReviewGet(null, "", session)

        then:
        response.statusCode.value() == 200
        // Card 1 of 3
        response.body.contains("1")
        response.body.contains("3")
    }

    // =========================================================================
    // POST /flashcards/review/random - submit rating
    // =========================================================================

    def "POST /flashcards/review/random with valid quality returns 302 redirect"() {
        given:
        writeCard("java/card.yaml", minimalYaml("Q?", "A."))
        def session = new MockHttpSession()
        controller.randomReviewGet(1, "", session)

        when:
        def response = controller.randomReviewPost("java/card.yaml", 4, session)

        then:
        response.statusCode.value() == 302
        response.headers.getFirst("Location") == "/flashcards/review/random"
    }

    def "POST /flashcards/review/random with invalid quality returns 400"() {
        given:
        def session = new MockHttpSession()

        when:
        def response = controller.randomReviewPost("any/path.yaml", 6, session)

        then:
        response.statusCode.value() == 400
    }

    def "POST /flashcards/review/random removes card from session queue"() {
        given:
        writeCard("java/card1.yaml", minimalYaml("Q1", "A1"))
        writeCard("java/card2.yaml", minimalYaml("Q2", "A2"))
        def session = new MockHttpSession()
        controller.randomReviewGet(2, "", session)

        when:
        // Get first card from queue
        def getResponse = controller.randomReviewGet(null, "", session)
        def queue = session.getAttribute("FC_RANDOM_QUEUE") as List<String>
        def firstPath = queue[0]
        controller.randomReviewPost(firstPath, 4, session)

        then:
        (session.getAttribute("FC_RANDOM_QUEUE") as List).size() == 1
    }

    def "POST /flashcards/review/random applies SM-2 and persists changes"() {
        given:
        writeCard("test/card.yaml", minimalYaml("Q?", "A."))
        def session = new MockHttpSession()
        controller.randomReviewGet(1, "", session)

        when:
        controller.randomReviewPost("test/card.yaml", 5, session)
        def card = service.loadCard("test/card.yaml").get()

        then:
        card.reviewCount == 1
        card.correctCount == 1
        card.lastReviewed != null
    }

    def "full random session cycle ends with session-complete page"() {
        given:
        writeCard("cycle/card1.yaml", minimalYaml("CQ1", "CA1"))
        writeCard("cycle/card2.yaml", minimalYaml("CQ2", "CA2"))
        def session = new MockHttpSession()

        when: "start a session with 2 cards"
        controller.randomReviewGet(2, "cycle", session)

        and: "review first card"
        def r1 = controller.randomReviewGet(null, "", session)
        def queue = session.getAttribute("FC_RANDOM_QUEUE") as List<String>
        controller.randomReviewPost(queue[0], 4, session)

        and: "review second card"
        def r2 = controller.randomReviewGet(null, "", session)
        def queue2 = session.getAttribute("FC_RANDOM_QUEUE") as List<String>
        controller.randomReviewPost(queue2[0], 3, session)

        and: "get the final page"
        def finalResponse = controller.randomReviewGet(null, "", session)

        then:
        finalResponse.statusCode.value() == 200
        finalResponse.body.contains("Session complete")
    }
}

