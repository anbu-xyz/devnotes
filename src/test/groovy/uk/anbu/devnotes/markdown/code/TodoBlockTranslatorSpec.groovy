package uk.anbu.devnotes.markdown.code

import org.jsoup.Jsoup
import spock.lang.Shared
import spock.lang.Specification
import uk.anbu.devnotes.markdown.code.todo.TodoConfig

import java.time.LocalDate

class TodoBlockTranslatorSpec extends Specification {

    @Shared
    TodoBlockTranslator translator

    def setupSpec() {
        translator = new TodoBlockTranslator()
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private static String yamlWithItem(String itemExtra = "") {
        """\
thresholds:
  age:
    green:  7
    amber: 14
    red:   30
  due-in:
    green: 30
    amber: 14
    red:    7
items:
  - summary: Fix login bug
    created: ${LocalDate.now()}
    due: ${LocalDate.now().plusDays(10)}
    description: "See **ticket** #1234."
    ${itemExtra}
"""
    }

    // =========================================================================
    // 1. Card structure
    // =========================================================================

    def "full item renders summary as h3 with todo-summary class"() {
        given:
        def yaml = yamlWithItem()

        when:
        def result = translator.translate(yaml)

        then:
        result.isPresent()
        def h3 = Jsoup.parse(result.get().literal).select("h3.todo-summary")
        h3.size() == 1
        h3[0].text() == "Fix login bug"
    }

    def "full item produces exactly one todo-item card"() {
        given:
        def yaml = yamlWithItem()

        when:
        def result = translator.translate(yaml)

        then:
        Jsoup.parse(result.get().literal).select(".todo-item").size() == 1
    }

    // =========================================================================
    // 2. Open (days) meta text
    // =========================================================================

    def "open days text is hidden when item age is green (created today)"() {
        given:
        def yaml = """\
items:
  - summary: New item
    created: ${LocalDate.now()}
"""
        when:
        def result = translator.translate(yaml)

        then:
        !Jsoup.parse(result.get().literal).select(".todo-meta").text().contains("Open:")
    }

    def "open days text is hidden when item age is green (created five days ago)"() {
        given:
        def yaml = """\
items:
  - summary: Old item
    created: ${LocalDate.now().minusDays(5)}
"""
        when:
        def result = translator.translate(yaml)

        then:
        !Jsoup.parse(result.get().literal).select(".todo-meta").text().contains("Open:")
    }

    def "open days text is hidden when created date is absent"() {
        given:
        def yaml = """\
items:
  - summary: No created date
"""
        when:
        def result = translator.translate(yaml)

        then:
        !Jsoup.parse(result.get().literal).select(".todo-meta").text().contains("Open:")
    }

    def "open days text shows correct day count when age is amber"() {
        given:
        def yaml = """\
items:
  - summary: Getting old
    created: ${LocalDate.now().minusDays(8)}
"""
        when:
        def result = translator.translate(yaml)

        then:
        Jsoup.parse(result.get().literal).select(".todo-meta small").any { it.text().contains("Open: 8 days") }
    }

    // =========================================================================
    // 3. Due in meta text
    // =========================================================================

    def "due in text shows positive days when due date is in the future"() {
        given:
        def yaml = """\
items:
  - summary: Future due
    created: ${LocalDate.now()}
    due: ${LocalDate.now().plusDays(10)}
"""
        when:
        def result = translator.translate(yaml)

        then:
        Jsoup.parse(result.get().literal).select(".todo-meta small").any { it.text().contains("Due in: 10 days") }
    }

    def "due in text shows negative days when item is past due"() {
        given:
        def yaml = """\
items:
  - summary: Overdue item
    created: ${LocalDate.now().minusDays(10)}
    due: ${LocalDate.now().minusDays(3)}
"""
        when:
        def result = translator.translate(yaml)

        then:
        Jsoup.parse(result.get().literal).select(".todo-meta small").any { it.text().contains("Due in: -3 days") }
    }

    def "due in text is hidden when due date is absent"() {
        given:
        def yaml = """\
items:
  - summary: No deadline
    created: ${LocalDate.now()}
"""
        when:
        def result = translator.translate(yaml)

        then:
        !Jsoup.parse(result.get().literal).select(".todo-meta").text().contains("Due in:")
    }

    // =========================================================================
    // 4. Color coding — age-driven
    // =========================================================================

    def "item created today (no due) gets todo-green class from age"() {
        given:
        def yaml = """\
items:
  - summary: Brand new
    created: ${LocalDate.now()}
"""
        when:
        def result = translator.translate(yaml)

        then:
        Jsoup.parse(result.get().literal).select(".todo-item")[0].hasClass("todo-green")
    }

    def "item 8 days old (no due) gets todo-amber class from age"() {
        given:
        def yaml = """\
items:
  - summary: Getting old
    created: ${LocalDate.now().minusDays(8)}
"""
        when:
        def result = translator.translate(yaml)

        then:
        Jsoup.parse(result.get().literal).select(".todo-item")[0].hasClass("todo-amber")
    }

    def "item 15 days old (no due) gets todo-red class from age"() {
        given:
        def yaml = """\
items:
  - summary: Old
    created: ${LocalDate.now().minusDays(15)}
"""
        when:
        def result = translator.translate(yaml)

        then:
        Jsoup.parse(result.get().literal).select(".todo-item")[0].hasClass("todo-red")
    }

    def "item 31 days old (no due) gets todo-overdue class from age"() {
        given:
        def yaml = """\
items:
  - summary: Very old
    created: ${LocalDate.now().minusDays(31)}
"""
        when:
        def result = translator.translate(yaml)

        then:
        Jsoup.parse(result.get().literal).select(".todo-item")[0].hasClass("todo-overdue")
    }

    // =========================================================================
    // 5. Color coding — due-in-driven
    // =========================================================================

    def "item with due date 35 days away (created today) gets todo-green class from due-in"() {
        given:
        def yaml = """\
items:
  - summary: Loads of time
    created: ${LocalDate.now()}
    due: ${LocalDate.now().plusDays(35)}
"""
        when:
        def result = translator.translate(yaml)

        then:
        Jsoup.parse(result.get().literal).select(".todo-item")[0].hasClass("todo-green")
    }

    def "item due in 16 days (created today) gets todo-amber class from due-in"() {
        given:
        def yaml = """\
items:
  - summary: Some time left
    created: ${LocalDate.now()}
    due: ${LocalDate.now().plusDays(16)}
"""
        when:
        def result = translator.translate(yaml)

        then:
        Jsoup.parse(result.get().literal).select(".todo-item")[0].hasClass("todo-amber")
    }

    def "item due in 9 days (created today) gets todo-red class from due-in"() {
        given:
        def yaml = """\
items:
  - summary: Getting urgent
    created: ${LocalDate.now()}
    due: ${LocalDate.now().plusDays(9)}
"""
        when:
        def result = translator.translate(yaml)

        then:
        Jsoup.parse(result.get().literal).select(".todo-item")[0].hasClass("todo-red")
    }

    def "item with past due date (created today) gets todo-overdue class from due-in"() {
        given:
        def yaml = """\
items:
  - summary: Past deadline
    created: ${LocalDate.now()}
    due: ${LocalDate.now().minusDays(1)}
"""
        when:
        def result = translator.translate(yaml)

        then:
        Jsoup.parse(result.get().literal).select(".todo-item")[0].hasClass("todo-overdue")
    }

    // =========================================================================
    // 6. Highest criticality wins
    // =========================================================================

    def "when age gives amber and due-in gives overdue, row class is todo-overdue"() {
        given:
        // created 10 days ago → amber by age (between green=7 and amber=14)
        // due yesterday → overdue by due-in
        def yaml = """\
items:
  - summary: Urgent and ageing
    created: ${LocalDate.now().minusDays(10)}
    due: ${LocalDate.now().minusDays(1)}
"""
        when:
        def result = translator.translate(yaml)

        then:
        Jsoup.parse(result.get().literal).select(".todo-item")[0].hasClass("todo-overdue")
    }

    def "when age gives overdue and due-in gives amber, row class is todo-overdue"() {
        given:
        // created 31 days ago → overdue by age (≥ red=30)
        // due in 16 days → amber by due-in (between amber=14 and green=30)
        def yaml = """\
items:
  - summary: Very old but has time
    created: ${LocalDate.now().minusDays(31)}
    due: ${LocalDate.now().plusDays(16)}
"""
        when:
        def result = translator.translate(yaml)

        then:
        Jsoup.parse(result.get().literal).select(".todo-item")[0].hasClass("todo-overdue")
    }

    def "when age gives red and due-in gives amber, row class is todo-red"() {
        given:
        // created 20 days ago → red by age (between amber=14 and red=30)
        // due in 20 days → amber by due-in (between amber=14 and green=30)
        def yaml = """\
items:
  - summary: Red age, amber due-in
    created: ${LocalDate.now().minusDays(20)}
    due: ${LocalDate.now().plusDays(20)}
"""
        when:
        def result = translator.translate(yaml)

        then:
        Jsoup.parse(result.get().literal).select(".todo-item")[0].hasClass("todo-red")
    }

    // =========================================================================
    // 7. Separate age and due-in thresholds
    // =========================================================================

    def "separate age and due-in thresholds are applied independently"() {
        given:
        // age thresholds: green=3, amber=7, red=14  → item 5 days old → amber by age
        // due-in thresholds: green=10, amber=5, red=2 → due in 8 days → amber by due-in
        // highest = amber
        def yaml = """\
thresholds:
  age:
    green: 3
    amber: 7
    red: 14
  due-in:
    green: 10
    amber: 5
    red: 2
items:
  - summary: Custom thresholds
    created: ${LocalDate.now().minusDays(5)}
    due: ${LocalDate.now().plusDays(8)}
"""
        when:
        def result = translator.translate(yaml)

        then:
        Jsoup.parse(result.get().literal).select(".todo-item")[0].hasClass("todo-amber")
    }

    def "tighter due-in threshold escalates colour when age would give green"() {
        given:
        // created today → green by age
        // due-in thresholds: green=90, amber=60, red=30 → due in 15 days → overdue by due-in
        def yaml = """\
thresholds:
  age:
    green: 7
    amber: 14
    red: 30
  due-in:
    green: 90
    amber: 60
    red: 30
items:
  - summary: Tight due-in threshold
    created: ${LocalDate.now()}
    due: ${LocalDate.now().plusDays(15)}
"""
        when:
        def result = translator.translate(yaml)

        then:
        Jsoup.parse(result.get().literal).select(".todo-item")[0].hasClass("todo-overdue")
    }

    // =========================================================================
    // 8. Defaults and absent fields
    // =========================================================================

    def "default thresholds are applied when thresholds block is absent"() {
        given:
        // Default age.green=7: item 6 days old → green
        def yaml = """\
items:
  - summary: No thresholds
    created: ${LocalDate.now().minusDays(6)}
"""
        when:
        def result = translator.translate(yaml)

        then:
        Jsoup.parse(result.get().literal).select(".todo-item")[0].hasClass("todo-green")
    }

    def "item without created date defaults to green for age dimension"() {
        given:
        // No created → age class = green; due far away → due-in class = green; result = green
        def yaml = """\
items:
  - summary: No created
    due: ${LocalDate.now().plusDays(60)}
"""
        when:
        def result = translator.translate(yaml)

        then:
        Jsoup.parse(result.get().literal).select(".todo-item")[0].hasClass("todo-green")
    }

    def "item without due date defaults to green for due-in dimension"() {
        given:
        // created today → green by age; no due → due-in = green; result = green
        def yaml = """\
items:
  - summary: No deadline
    created: ${LocalDate.now()}
"""
        when:
        def result = translator.translate(yaml)

        then:
        Jsoup.parse(result.get().literal).select(".todo-item")[0].hasClass("todo-green")
    }

    // =========================================================================
    // 9. Legacy color-mode field is silently ignored
    // =========================================================================

    def "old color-mode field in YAML is silently ignored and does not cause an error"() {
        given:
        def yaml = """\
color-mode: urgency
items:
  - summary: Legacy block
    created: ${LocalDate.now()}
"""
        when:
        def result = translator.translate(yaml)

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        doc.select(".todo-error").size() == 0
        doc.select(".todo-item").size() == 1
    }

    // =========================================================================
    // 10. Markdown description
    // =========================================================================

    def "description markdown bold text is rendered as an HTML strong element"() {
        given:
        def yaml = """\
items:
  - summary: Markdown test
    created: ${LocalDate.now()}
    description: "See **ticket** #1234."
"""
        when:
        def result = translator.translate(yaml)

        then:
        def descDiv = Jsoup.parse(result.get().literal).select(".todo-description")
        descDiv.select("strong").size() == 1
        descDiv.select("strong")[0].text() == "ticket"
    }

    // =========================================================================
    // 11. Error handling
    // =========================================================================

    def "malformed YAML returns error block with todo-error class"() {
        given:
        def yaml = "this: {is: :bad: yaml"

        when:
        def result = translator.translate(yaml)

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        doc.select(".todo-error").size() == 1
        doc.select(".todo-item").size() == 0
    }

    // =========================================================================
    // 12. Empty / multiple items
    // =========================================================================

    def "absent items list renders an empty block without error"() {
        given:
        def yaml = """\
thresholds:
  age:
    green: 7
"""
        when:
        def result = translator.translate(yaml)

        then:
        result.isPresent()
        def doc = Jsoup.parse(result.get().literal)
        doc.select(".todo-block").size() == 1
        doc.select(".todo-item").size() == 0
    }

    def "multiple items all appear as cards in the block"() {
        given:
        def yaml = """\
items:
  - summary: Item one
    created: ${LocalDate.now()}
  - summary: Item two
    created: ${LocalDate.now().minusDays(10)}
  - summary: Item three
    created: ${LocalDate.now().minusDays(20)}
"""
        when:
        def result = translator.translate(yaml)

        then:
        Jsoup.parse(result.get().literal).select(".todo-item").size() == 3
    }

    // =========================================================================
    // 13. Unit tests for computeRowClass
    // =========================================================================

    def "computeRowClass returns correct class driven purely by age"() {
        given:
        def config = new TodoConfig()
        def item = new TodoConfig.TodoItem()
        item.created = LocalDate.now().minusDays(daysOld)
        // no due date → due-in always green

        expect:
        translator.computeRowClass(config, item) == expectedClass

        where:
        daysOld | expectedClass
        0       | "todo-green"
        6       | "todo-green"
        7       | "todo-amber"
        13      | "todo-amber"
        14      | "todo-red"
        29      | "todo-red"
        30      | "todo-overdue"
        100     | "todo-overdue"
    }

    def "computeRowClass returns correct class driven purely by due-in"() {
        given:
        def config = new TodoConfig()
        def item = new TodoConfig.TodoItem()
        item.created = LocalDate.now()  // created today → always green by age
        item.due = dueDate

        expect:
        translator.computeRowClass(config, item) == expectedClass

        where:
        dueDate                           | expectedClass
        null                              | "todo-green"
        LocalDate.now().plusDays(35)      | "todo-green"
        LocalDate.now().plusDays(31)      | "todo-green"
        LocalDate.now().plusDays(30)      | "todo-amber"
        LocalDate.now().plusDays(15)      | "todo-amber"
        LocalDate.now().plusDays(14)      | "todo-red"
        LocalDate.now().plusDays(8)       | "todo-red"
        LocalDate.now().plusDays(7)       | "todo-overdue"
        LocalDate.now()                   | "todo-overdue"
        LocalDate.now().minusDays(1)      | "todo-overdue"
    }

    def "computeRowClass picks highest criticality between age and due-in"() {
        given:
        def config = new TodoConfig()
        def item = new TodoConfig.TodoItem()
        item.created = LocalDate.now().minusDays(ageClass == "todo-overdue" ? 31 :
                                                  ageClass == "todo-red"     ? 20 :
                                                  ageClass == "todo-amber"   ? 10 : 0)
        // wire up due-in via daysLeft
        item.due = LocalDate.now().plusDays(dueInDaysLeft)

        expect:
        translator.computeRowClass(config, item) == expectedClass

        where:
        ageClass       | dueInDaysLeft | expectedClass
        "todo-green"   | -1            | "todo-overdue"   // due-in wins
        "todo-amber"   | 35            | "todo-amber"     // age wins (tie: age=amber, due-in=green)
        "todo-red"     | 16            | "todo-red"       // age wins (red > amber)
        "todo-overdue" | 16            | "todo-overdue"   // age wins
    }

    // =========================================================================
    // 14. Unit tests for computeOpenDays and computeDueIn
    // =========================================================================

    def "computeOpenDays returns em-dash when created is absent"() {
        given:
        def item = new TodoConfig.TodoItem()

        expect:
        translator.computeOpenDays(item) == "\u2014"
    }

    def "computeOpenDays returns correct day count"() {
        given:
        def item = new TodoConfig.TodoItem()
        item.created = LocalDate.now().minusDays(7)

        expect:
        translator.computeOpenDays(item) == "7"
    }

    def "computeDueIn returns em-dash when due is absent"() {
        given:
        def item = new TodoConfig.TodoItem()

        expect:
        translator.computeDueIn(item) == "\u2014"
    }

    def "computeDueIn returns positive count when due is in the future"() {
        given:
        def item = new TodoConfig.TodoItem()
        item.due = LocalDate.now().plusDays(5)

        expect:
        translator.computeDueIn(item) == "5"
    }

    def "computeDueIn returns negative count when due is in the past"() {
        given:
        def item = new TodoConfig.TodoItem()
        item.due = LocalDate.now().minusDays(4)

        expect:
        translator.computeDueIn(item) == "-4"
    }

    // =========================================================================
    // 15. Open days and due-in visibility
    // =========================================================================

    def "open days text is shown when age is non-green and no due date"() {
        given:
        // created 20 days ago → age red (between amber=14 and red=30); no due
        def yaml = """\
items:
  - summary: Age-driven
    created: ${LocalDate.now().minusDays(20)}
"""
        when:
        def result = translator.translate(yaml)

        then:
        def metaText = Jsoup.parse(result.get().literal).select(".todo-meta").text()
        metaText.contains("Open:")
        !metaText.contains("Due in:")
    }

    def "open days text is hidden when age is green even with non-green due-in"() {
        given:
        // created today → age green; due in 9 days → due-in red
        def yaml = """\
items:
  - summary: Due-in-driven
    created: ${LocalDate.now()}
    due: ${LocalDate.now().plusDays(9)}
"""
        when:
        def result = translator.translate(yaml)

        then:
        def metaText = Jsoup.parse(result.get().literal).select(".todo-meta").text()
        !metaText.contains("Open:")
        metaText.contains("Due in:")
    }

    def "both open days and due in are shown when both age and due-in are non-green"() {
        given:
        // created 20 days ago → age red; due in 9 days → due-in red
        def yaml = """\
items:
  - summary: Both non-green
    created: ${LocalDate.now().minusDays(20)}
    due: ${LocalDate.now().plusDays(9)}
"""
        when:
        def result = translator.translate(yaml)

        then:
        def metaText = Jsoup.parse(result.get().literal).select(".todo-meta").text()
        metaText.contains("Open:")
        metaText.contains("Due in:")
    }

    def "no meta element appears when age is green and no due date is set"() {
        given:
        // created today → age green (open days hidden); no due date → due-in hidden
        def yaml = """\
items:
  - summary: All good
    created: ${LocalDate.now()}
"""
        when:
        def result = translator.translate(yaml)

        then:
        Jsoup.parse(result.get().literal).select(".todo-meta").size() == 0
    }

    def "due in text is shown even when due-in class is green (due date present but far away)"() {
        given:
        // created today → age green; due in 35 days → due-in class green, but date IS set → show
        def yaml = """\
items:
  - summary: All good
    created: ${LocalDate.now()}
    due: ${LocalDate.now().plusDays(35)}
"""
        when:
        def result = translator.translate(yaml)

        then:
        def metaText = Jsoup.parse(result.get().literal).select(".todo-meta").text()
        !metaText.contains("Open:")
        metaText.contains("Due in: 35 days")
    }

    // =========================================================================
    // Status field
    // =========================================================================

    def "item with status 'not-started' shows Not started badge"() {
        given:
        def yaml = """\
items:
  - summary: Pending task
    created: ${LocalDate.now()}
    status: not-started
"""
        when:
        def result = translator.translate(yaml)

        then:
        def badge = Jsoup.parse(result.get().literal).select(".todo-status-badge")
        badge.size() == 1
        badge[0].text() == "Not started"
        badge[0].hasClass("todo-status-not-started")
    }

    def "item with status 'in-progress' shows In progress badge"() {
        given:
        def yaml = """\
items:
  - summary: Active task
    created: ${LocalDate.now()}
    status: in-progress
"""
        when:
        def result = translator.translate(yaml)

        then:
        def badge = Jsoup.parse(result.get().literal).select(".todo-status-badge")
        badge.size() == 1
        badge[0].text() == "In progress"
        badge[0].hasClass("todo-status-in-progress")
    }

    def "item with status 'completed' shows Completed badge"() {
        given:
        def yaml = """\
items:
  - summary: Done task
    created: ${LocalDate.now()}
    status: completed
"""
        when:
        def result = translator.translate(yaml)

        then:
        def badge = Jsoup.parse(result.get().literal).select(".todo-status-badge")
        badge.size() == 1
        badge[0].text() == "Completed"
        badge[0].hasClass("todo-status-completed")
    }

    def "item with status 'completed' gets todo-completed CSS class on the item div"() {
        given:
        def yaml = """\
items:
  - summary: Done task
    created: ${LocalDate.now()}
    status: completed
"""
        when:
        def result = translator.translate(yaml)

        then:
        Jsoup.parse(result.get().literal).select(".todo-item")[0].hasClass("todo-completed")
    }

    def "item without status has no status badge"() {
        given:
        def yaml = """\
items:
  - summary: No status
    created: ${LocalDate.now()}
"""
        when:
        def result = translator.translate(yaml)

        then:
        Jsoup.parse(result.get().literal).select(".todo-status-badge").size() == 0
    }

    def "item without status does not get todo-completed CSS class"() {
        given:
        def yaml = """\
items:
  - summary: No status
    created: ${LocalDate.now()}
"""
        when:
        def result = translator.translate(yaml)

        then:
        !Jsoup.parse(result.get().literal).select(".todo-item")[0].hasClass("todo-completed")
    }

    def "status badge appears even when age is green and no due date (status forces meta div)"() {
        given:
        def yaml = """\
items:
  - summary: In flight
    created: ${LocalDate.now()}
    status: in-progress
"""
        when:
        def result = translator.translate(yaml)

        then:
        Jsoup.parse(result.get().literal).select(".todo-meta").size() == 1
        Jsoup.parse(result.get().literal).select(".todo-status-badge").size() == 1
    }

    // =========================================================================
    // Widget structure — filter bar, data attributes, next-state button
    // =========================================================================

    def "translated block is wrapped in a todo-widget div with a data-todo-id attribute"() {
        given:
        def yaml = """\
items:
  - summary: A task
    created: ${LocalDate.now()}
"""
        when:
        def result = translator.translate(yaml)

        then:
        def widget = Jsoup.parse(result.get().literal).select(".todo-widget")
        widget.size() == 1
        !widget[0].attr("data-todo-id").blank
    }

    def "filter bar contains checkboxes for not-started, in-progress, and completed"() {
        given:
        def yaml = """\
items:
  - summary: A task
"""
        when:
        def result = translator.translate(yaml)

        then:
        def checkboxes = Jsoup.parse(result.get().literal).select(".todo-filter-cb")
        checkboxes.size() == 3
        checkboxes.collect { it.attr("data-filter-status") }.toSet() ==
                ["not-started", "in-progress", "completed"].toSet()
    }

    def "all filter checkboxes are checked by default"() {
        given:
        def yaml = """\
items:
  - summary: A task
"""
        when:
        def result = translator.translate(yaml)

        then:
        Jsoup.parse(result.get().literal).select(".todo-filter-cb").every { it.hasAttr("checked") }
    }

    def "each todo item carries a data-item-index attribute starting at 0"() {
        given:
        def yaml = """\
items:
  - summary: First
  - summary: Second
  - summary: Third
"""
        when:
        def result = translator.translate(yaml)

        then:
        def items = Jsoup.parse(result.get().literal).select(".todo-item")
        items.size() == 3
        items.collect { it.attr("data-item-index") } == ["0", "1", "2"]
    }

    def "each todo item carries a data-status attribute matching its status key"() {
        given:
        def yaml = """\
items:
  - summary: Not started
    status: not-started
  - summary: In progress
    status: in-progress
  - summary: Completed
    status: completed
  - summary: No status
"""
        when:
        def result = translator.translate(yaml)

        then:
        def items = Jsoup.parse(result.get().literal).select(".todo-item")
        items[0].attr("data-status") == "not-started"
        items[1].attr("data-status") == "in-progress"
        items[2].attr("data-status") == "completed"
        items[3].attr("data-status") == ""
    }

    def "each todo item contains exactly one next-state button"() {
        given:
        def yaml = """\
items:
  - summary: Task one
  - summary: Task two
"""
        when:
        def result = translator.translate(yaml)

        then:
        Jsoup.parse(result.get().literal).select(".todo-next-state-btn").size() == 2
    }

    def "next-state button title says 'Set to Not started' when item has no status"() {
        given:
        def yaml = """\
items:
  - summary: New task
"""
        when:
        def result = translator.translate(yaml)

        then:
        Jsoup.parse(result.get().literal).select(".todo-next-state-btn")[0].attr("title") == "Set to Not started"
    }

    def "next-state button title reflects the correct next state for each status"() {
        given:
        def yaml = """\
items:
  - summary: NS
    status: not-started
  - summary: IP
    status: in-progress
  - summary: Done
    status: completed
"""
        when:
        def result = translator.translate(yaml)

        then:
        def btns = Jsoup.parse(result.get().literal).select(".todo-next-state-btn")
        btns[0].attr("title") == "Advance to In progress"
        btns[1].attr("title") == "Advance to Completed"
        btns[2].attr("title") == "Reset to Not started"
    }

    def "two blocks with identical YAML get the same data-todo-id"() {
        given:
        def yaml = """\
items:
  - summary: Identical
"""
        when:
        def r1 = translator.translate(yaml)
        def r2 = translator.translate(yaml)

        then:
        Jsoup.parse(r1.get().literal).select(".todo-widget")[0].attr("data-todo-id") ==
        Jsoup.parse(r2.get().literal).select(".todo-widget")[0].attr("data-todo-id")
    }

    // =========================================================================
    // Filter-config persistence in YAML
    // =========================================================================

    def "all checkboxes are checked when no filters section is present"() {
        given:
        def yaml = "items:\n  - summary: A task\n"

        when:
        def result = translator.translate(yaml)

        then:
        def cbs = Jsoup.parse(result.get().literal).select(".todo-filter-cb")
        cbs.every { it.hasAttr("checked") }
    }

    def "completed checkbox is unchecked when filters.completed is false in YAML"() {
        given:
        def yaml = """\
filters:
  completed: false
items:
  - summary: A task
"""
        when:
        def result = translator.translate(yaml)

        then:
        def cbs = Jsoup.parse(result.get().literal).select(".todo-filter-cb")
        def completedCb = cbs.find { it.attr("data-filter-status") == "completed" }
        !completedCb.hasAttr("checked")
        // other two are still checked
        cbs.findAll { it.attr("data-filter-status") != "completed" }.every { it.hasAttr("checked") }
    }

    def "not-started and in-progress checkboxes are unchecked when hidden in YAML"() {
        given:
        def yaml = """\
filters:
  not-started: false
  in-progress: false
items:
  - summary: A task
"""
        when:
        def result = translator.translate(yaml)

        then:
        def cbs = Jsoup.parse(result.get().literal).select(".todo-filter-cb")
        !cbs.find { it.attr("data-filter-status") == "not-started" }.hasAttr("checked")
        !cbs.find { it.attr("data-filter-status") == "in-progress" }.hasAttr("checked")
        cbs.find  { it.attr("data-filter-status") == "completed"   }.hasAttr("checked")
    }
}

