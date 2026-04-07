package uk.anbu.devnotes.controller

import org.jsoup.Jsoup
import org.springframework.http.HttpStatus
import spock.lang.Specification
import spock.lang.TempDir
import uk.anbu.devnotes.service.ConfigService

import java.nio.file.Files
import java.nio.file.Path

import static uk.anbu.devnotes.util.FileBasedCache.generateHash

class TodoStatusControllerSpec extends Specification {

    @TempDir
    Path tempDir

    ConfigService configService
    TodoStatusController controller

    def setup() {
        configService = Mock(ConfigService)
        configService.getDocsDirectory() >> tempDir.toString()
        controller = new TodoStatusController(configService)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Write a markdown file containing one ```todo block and return the relative filename. */
    private String writeMd(String yamlBody) {
        def file = tempDir.resolve("notes.md")
        // Use stripTrailing() to mirror how real markdown files are stored:
        // no blank line between the last YAML line and the closing fence.
        Files.writeString(file, "# Notes\n\n```todo\n${yamlBody.stripTrailing()}\n```\n")
        return "notes.md"
    }

    /** Compute the todo-widget ID for a YAML body, mirroring TodoBlockTranslator. */
    private static String todoId(String yaml) {
        generateHash(yaml.stripTrailing())
    }

    // ── input validation ──────────────────────────────────────────────────────

    def "returns 400 when markdownFile is missing"() {
        when:
        def resp = controller.advanceStatus([todoId: "abc", itemIndex: 0])

        then:
        resp.statusCode == HttpStatus.BAD_REQUEST
    }

    def "returns 400 when todoId is missing"() {
        when:
        def resp = controller.advanceStatus([markdownFile: "notes.md", itemIndex: 0])

        then:
        resp.statusCode == HttpStatus.BAD_REQUEST
    }

    def "returns 400 when itemIndex is not an integer"() {
        given:
        def yaml = "items:\n  - summary: Task\n"
        def filename = writeMd(yaml)

        when:
        def resp = controller.advanceStatus([
            markdownFile: filename,
            todoId: todoId(yaml),
            itemIndex: "notanumber"
        ])

        then:
        resp.statusCode == HttpStatus.BAD_REQUEST
    }

    def "returns 404 when markdown file does not exist"() {
        when:
        def resp = controller.advanceStatus([
            markdownFile: "missing.md",
            todoId: "deadbeef",
            itemIndex: 0
        ])

        then:
        resp.statusCode == HttpStatus.NOT_FOUND
    }

    def "returns 404 when todoId does not match any block in the file"() {
        given:
        def yaml = "items:\n  - summary: Task\n"
        def filename = writeMd(yaml)

        when:
        def resp = controller.advanceStatus([
            markdownFile: filename,
            todoId: "00000000wrongid0",
            itemIndex: 0
        ])

        then:
        resp.statusCode == HttpStatus.NOT_FOUND
    }

    // ── happy path — status transitions ───────────────────────────────────────

    def "advances status from null to not-started and returns HTML widget"() {
        given:
        def yaml = "items:\n  - summary: Fresh task\n"
        def filename = writeMd(yaml)

        when:
        def resp = controller.advanceStatus([
            markdownFile: filename,
            todoId: todoId(yaml),
            itemIndex: 0
        ])

        then:
        resp.statusCode == HttpStatus.OK
        def doc = Jsoup.parse(resp.body)
        doc.select(".todo-status-badge")[0].text() == "Not started"
        doc.select(".todo-widget").size() == 1
    }

    def "advances status from not-started to in-progress"() {
        given:
        def yaml = "items:\n  - summary: Task\n    status: not-started\n"
        def filename = writeMd(yaml)

        when:
        def resp = controller.advanceStatus([
            markdownFile: filename,
            todoId: todoId(yaml),
            itemIndex: 0
        ])

        then:
        resp.statusCode == HttpStatus.OK
        Jsoup.parse(resp.body).select(".todo-status-badge")[0].text() == "In progress"
    }

    def "advances status from in-progress to completed"() {
        given:
        def yaml = "items:\n  - summary: Task\n    status: in-progress\n"
        def filename = writeMd(yaml)

        when:
        def resp = controller.advanceStatus([
            markdownFile: filename,
            todoId: todoId(yaml),
            itemIndex: 0
        ])

        then:
        resp.statusCode == HttpStatus.OK
        Jsoup.parse(resp.body).select(".todo-status-badge")[0].text() == "Completed"
    }

    def "advances status from completed back to not-started"() {
        given:
        def yaml = "items:\n  - summary: Task\n    status: completed\n"
        def filename = writeMd(yaml)

        when:
        def resp = controller.advanceStatus([
            markdownFile: filename,
            todoId: todoId(yaml),
            itemIndex: 0
        ])

        then:
        resp.statusCode == HttpStatus.OK
        Jsoup.parse(resp.body).select(".todo-status-badge")[0].text() == "Not started"
    }

    def "updates the correct item when multiple items are present"() {
        given:
        def yaml = """\
items:
  - summary: First
    status: not-started
  - summary: Second
    status: not-started
  - summary: Third
    status: not-started
"""
        def filename = writeMd(yaml)

        when:
        def resp = controller.advanceStatus([
            markdownFile: filename,
            todoId: todoId(yaml),
            itemIndex: 1          // advance only the middle item
        ])

        then:
        resp.statusCode == HttpStatus.OK
        def items = Jsoup.parse(resp.body).select(".todo-status-badge")
        items[0].text() == "Not started"
        items[1].text() == "In progress"
        items[2].text() == "Not started"
    }

    // ── file persistence ───────────────────────────────────────────────────────

    def "persists the updated status to the markdown file"() {
        given:
        def yaml = "items:\n  - summary: Persistent task\n"
        def filename = writeMd(yaml)

        when:
        def resp = controller.advanceStatus([
            markdownFile: filename,
            todoId: todoId(yaml),
            itemIndex: 0
        ])

        then:
        resp.statusCode == HttpStatus.OK
        def savedContent = Files.readString(tempDir.resolve(filename))
        savedContent.contains("not-started")
    }

    def "returned HTML contains updated data-todo-id reflecting new YAML content"() {
        given:
        def yaml = "items:\n  - summary: A task\n"
        def filename = writeMd(yaml)
        def oldId = todoId(yaml)

        when:
        def resp = controller.advanceStatus([
            markdownFile: filename,
            todoId: oldId,
            itemIndex: 0
        ])

        then:
        resp.statusCode == HttpStatus.OK
        // The new widget should have a different (updated) hash
        def newId = Jsoup.parse(resp.body).select(".todo-widget")[0].attr("data-todo-id")
        newId != oldId
        !newId.blank
    }

    // ── save-filters ──────────────────────────────────────────────────────────

    def "save-filters returns 400 when markdownFile is missing"() {
        when:
        def resp = controller.saveFilters([todoId: "abc", "completed": false])

        then:
        resp.statusCode == HttpStatus.BAD_REQUEST
    }

    def "save-filters returns 404 when todoId does not match any block"() {
        given:
        def yaml = "items:\n  - summary: Task\n"
        def filename = writeMd(yaml)

        when:
        def resp = controller.saveFilters([markdownFile: filename, todoId: "deadbeef00000000", "completed": false])

        then:
        resp.statusCode == HttpStatus.NOT_FOUND
    }

    def "save-filters persists completed=false to the YAML file"() {
        given:
        def yaml = "items:\n  - summary: Task\n"
        def filename = writeMd(yaml)

        when:
        def resp = controller.saveFilters([
            markdownFile: filename,
            todoId: todoId(yaml),
            "not-started": true,
            "in-progress": true,
            "completed": false
        ])

        then:
        resp.statusCode == HttpStatus.OK
        def saved = Files.readString(tempDir.resolve(filename))
        saved.contains("completed: false")
    }

    def "save-filters omits filters section when all are visible"() {
        given:
        def yaml = "filters:\n  completed: false\nitems:\n  - summary: Task\n"
        def filename = writeMd(yaml)

        when:
        def resp = controller.saveFilters([
            markdownFile: filename,
            todoId: todoId(yaml),
            "not-started": true,
            "in-progress": true,
            "completed": true
        ])

        then:
        resp.statusCode == HttpStatus.OK
        def saved = Files.readString(tempDir.resolve(filename))
        !saved.contains("filters:")
    }

    def "save-filters returns a new hash different from the original"() {
        given:
        def yaml = "items:\n  - summary: Task\n"
        def filename = writeMd(yaml)
        def original = todoId(yaml)

        when:
        def resp = controller.saveFilters([
            markdownFile: filename,
            todoId: original,
            "not-started": true,
            "in-progress": true,
            "completed": false
        ])

        then:
        resp.statusCode == HttpStatus.OK
        resp.body.trim() != original
        !resp.body.trim().blank
    }

    def "advance-status after save-filters uses the updated hash correctly"() {
        given:
        def yaml = "items:\n  - summary: Task\n"
        def filename = writeMd(yaml)

        // First save a filter
        def filterResp = controller.saveFilters([
            markdownFile: filename,
            todoId: todoId(yaml),
            "not-started": true,
            "in-progress": true,
            "completed": false
        ])
        def updatedTodoId = filterResp.body.trim()

        when:
        // Now advance status using the new hash
        def statusResp = controller.advanceStatus([
            markdownFile: filename,
            todoId: updatedTodoId,
            itemIndex: 0
        ])

        then:
        statusResp.statusCode == HttpStatus.OK
        Jsoup.parse(statusResp.body).select(".todo-status-badge")[0].text() == "Not started"
        // completed filter is still false in the returned HTML
        def completedCb = Jsoup.parse(statusResp.body).select(".todo-filter-cb")
                .find { it.attr("data-filter-status") == "completed" }
        !completedCb.hasAttr("checked")
    }

    // ── add-item ──────────────────────────────────────────────────────────────

    def "add-item returns 400 when markdownFile is missing"() {
        when:
        def resp = controller.addItem([todoId: "abc", summary: "New task"])

        then:
        resp.statusCode == HttpStatus.BAD_REQUEST
    }

    def "add-item returns 400 when summary is missing"() {
        given:
        def yaml = "items:\n  - summary: Existing\n"
        def filename = writeMd(yaml)

        when:
        def resp = controller.addItem([markdownFile: filename, todoId: todoId(yaml)])

        then:
        resp.statusCode == HttpStatus.BAD_REQUEST
    }

    def "add-item returns 404 when markdown file does not exist"() {
        when:
        def resp = controller.addItem([markdownFile: "missing.md", todoId: "deadbeef", summary: "Task"])

        then:
        resp.statusCode == HttpStatus.NOT_FOUND
    }

    def "add-item returns 404 when todoId does not match any block"() {
        given:
        def yaml = "items:\n  - summary: Existing\n"
        def filename = writeMd(yaml)

        when:
        def resp = controller.addItem([markdownFile: filename, todoId: "00000000wrongid0", summary: "Task"])

        then:
        resp.statusCode == HttpStatus.NOT_FOUND
    }

    def "add-item prepends new item at top of the list and returns HTML widget"() {
        given:
        def yaml = "items:\n  - summary: Existing task\n"
        def filename = writeMd(yaml)

        when:
        def resp = controller.addItem([
            markdownFile: filename,
            todoId: todoId(yaml),
            summary: "Brand new task"
        ])

        then:
        resp.statusCode == HttpStatus.OK
        def items = Jsoup.parse(resp.body).select(".todo-summary")
        items[0].text() == "Brand new task"
        items[1].text() == "Existing task"
    }

    def "add-item persists new item to the markdown file"() {
        given:
        def yaml = "items:\n  - summary: Original\n"
        def filename = writeMd(yaml)

        when:
        controller.addItem([
            markdownFile: filename,
            todoId: todoId(yaml),
            summary: "Persisted task"
        ])

        then:
        def saved = Files.readString(tempDir.resolve(filename))
        saved.contains("Persisted task")
    }

    def "add-item sets created date to today"() {
        given:
        def yaml = "items:\n  - summary: Old task\n"
        def filename = writeMd(yaml)

        when:
        controller.addItem([
            markdownFile: filename,
            todoId: todoId(yaml),
            summary: "Dated task"
        ])

        then:
        def saved = Files.readString(tempDir.resolve(filename))
        saved.contains(java.time.LocalDate.now().toString())
    }

    def "add-item stores optional due date when provided"() {
        given:
        def yaml = "items:\n  - summary: Existing\n"
        def filename = writeMd(yaml)

        when:
        def resp = controller.addItem([
            markdownFile: filename,
            todoId: todoId(yaml),
            summary: "Task with due",
            due: "2026-12-31"
        ])

        then:
        resp.statusCode == HttpStatus.OK
        Files.readString(tempDir.resolve(filename)).contains("2026-12-31")
    }

    def "add-item stores optional description when provided"() {
        given:
        def yaml = "items:\n  - summary: Existing\n"
        def filename = writeMd(yaml)

        when:
        def resp = controller.addItem([
            markdownFile: filename,
            todoId: todoId(yaml),
            summary: "Described task",
            description: "Some **detail** here"
        ])

        then:
        resp.statusCode == HttpStatus.OK
        Files.readString(tempDir.resolve(filename)).contains("Some **detail** here")
    }

    def "add-item stores status when provided"() {
        given:
        def yaml = "items:\n  - summary: Existing\n"
        def filename = writeMd(yaml)

        when:
        def resp = controller.addItem([
            markdownFile: filename,
            todoId: todoId(yaml),
            summary: "In-progress task",
            status: "in-progress"
        ])

        then:
        resp.statusCode == HttpStatus.OK
        Jsoup.parse(resp.body).select(".todo-status-badge")[0].text() == "In progress"
    }

    def "add-item returns new data-todo-id reflecting updated YAML"() {
        given:
        def yaml = "items:\n  - summary: Original\n"
        def filename = writeMd(yaml)
        def oldId = todoId(yaml)

        when:
        def resp = controller.addItem([
            markdownFile: filename,
            todoId: oldId,
            summary: "New task"
        ])

        then:
        resp.statusCode == HttpStatus.OK
        def newId = Jsoup.parse(resp.body).select(".todo-widget")[0].attr("data-todo-id")
        newId != oldId
        !newId.blank
    }
}