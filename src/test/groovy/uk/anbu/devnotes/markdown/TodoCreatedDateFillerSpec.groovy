package uk.anbu.devnotes.markdown

import spock.lang.Specification

import java.time.LocalDate

class TodoCreatedDateFillerSpec extends Specification {

    static final LocalDate TODAY = LocalDate.of(2026, 3, 31)
    static final String TODAY_STR = "2026-03-31"

    // =========================================================================
    // fillMissingCreatedDates - full-markdown level
    // =========================================================================

    def "markdown with no todo block is returned unchanged"() {
        given:
        def markdown = """\
# My Notes

Some content here.

```groovy:html
println "hello"
```
"""
        expect:
        TodoCreatedDateFiller.fillMissingCreatedDates(markdown, TODAY) == markdown
    }

    def "todo block where all items already have created dates is unchanged"() {
        given:
        def markdown = """\
```todo
items:
  - summary: Task one
    created: 2026-01-01
  - summary: Task two
    created: 2026-02-15
```
"""
        expect:
        TodoCreatedDateFiller.fillMissingCreatedDates(markdown, TODAY) == markdown
    }

    def "todo block with a single item missing created gets today injected"() {
        given:
        def markdown = """\
```todo
items:
  - summary: New task
    due: 2026-04-01
```
"""
        when:
        def result = TodoCreatedDateFiller.fillMissingCreatedDates(markdown, TODAY)

        then:
        result.contains("created: ${TODAY_STR}")
        result.contains("summary: New task")
        result.contains("due: 2026-04-01")
    }

    def "multiple items - only those without created get today injected"() {
        given:
        def markdown = """\
```todo
items:
  - summary: Already dated
    created: 2026-01-01
  - summary: Missing date
    due: 2026-05-01
  - summary: Also dated
    created: 2026-02-01
```
"""
        when:
        def result = TodoCreatedDateFiller.fillMissingCreatedDates(markdown, TODAY)

        then:
        def lines = result.readLines()

        def alreadyIdx = lines.findIndexOf { it.contains("Already dated") }
        !lines[alreadyIdx + 1].contains(TODAY_STR)      // keeps original date

        def missingIdx = lines.findIndexOf { it.contains("Missing date") }
        lines[missingIdx + 1].contains(TODAY_STR)        // injected

        def alsoIdx = lines.findIndexOf { it.contains("Also dated") }
        !lines[alsoIdx + 1].contains(TODAY_STR)          // keeps original date
    }

    def "multiple todo blocks in the same markdown are each processed independently"() {
        given:
        def markdown = """\
```todo
items:
  - summary: First block task
```

Some text between.

```todo
items:
  - summary: Second block task
    created: 2026-01-01
  - summary: Undated task
```
"""
        when:
        def result = TodoCreatedDateFiller.fillMissingCreatedDates(markdown, TODAY)

        then:
        result.count("created: ${TODAY_STR}") == 2
    }

    def "non-todo fenced block with 'items:' content is not modified"() {
        given:
        def markdown = """\
```groovy
items:
  - summary: not a todo
```
"""
        expect:
        TodoCreatedDateFiller.fillMissingCreatedDates(markdown, TODAY) == markdown
    }

    def "text surrounding todo block is preserved verbatim"() {
        given:
        def markdown = """\
# Title

Before the block.

```todo
items:
  - summary: Task
```

After the block.
"""
        when:
        def result = TodoCreatedDateFiller.fillMissingCreatedDates(markdown, TODAY)

        then:
        result.contains("# Title")
        result.contains("Before the block.")
        result.contains("After the block.")
        result.contains("created: ${TODAY_STR}")
    }

    // =========================================================================
    // injectCreatedDates - YAML body unit tests
    // =========================================================================

    def "yaml body with no items section is returned unchanged"() {
        given:
        def yaml = """\
thresholds:
  age:
    green: 7
"""
        expect:
        TodoCreatedDateFiller.injectCreatedDates(yaml, TODAY) == yaml
    }

    def "yaml body with empty items list is returned unchanged"() {
        given:
        def yaml = "items: []\n"

        expect:
        TodoCreatedDateFiller.injectCreatedDates(yaml, TODAY) == yaml
    }

    def "single item with only summary gets created inserted right after the opening line"() {
        given:
        def yaml = """\
items:
  - summary: Bare task"""

        when:
        def result = TodoCreatedDateFiller.injectCreatedDates(yaml, TODAY)

        then:
        def lines = result.readLines()
        def summaryIdx = lines.findIndexOf { it.contains("summary: Bare task") }
        lines[summaryIdx + 1] == "    created: ${TODAY_STR}"
    }

    def "created is inserted at correct 4-space indent and before existing properties"() {
        given:
        def yaml = """\
items:
  - summary: Task
    due: 2026-06-01
"""
        when:
        def result = TodoCreatedDateFiller.injectCreatedDates(yaml, TODAY)

        then:
        def lines = result.readLines()
        def summaryIdx = lines.findIndexOf { it.contains("summary: Task") }
        lines[summaryIdx + 1] == "    created: ${TODAY_STR}"
        lines[summaryIdx + 2].contains("due: 2026-06-01")
    }

    def "item with multi-line description still gets created injected"() {
        given:
        def yaml = """\
items:
  - summary: Detailed task
    description: |
      Line one
      Line two
"""
        when:
        def result = TodoCreatedDateFiller.injectCreatedDates(yaml, TODAY)

        then:
        result.contains("created: ${TODAY_STR}")
        result.contains("Line one")
        result.contains("Line two")
        def lines = result.readLines()
        def summaryIdx = lines.findIndexOf { it.contains("summary: Detailed task") }
        lines[summaryIdx + 1] == "    created: ${TODAY_STR}"
    }

    def "thresholds section after items does not prevent created injection"() {
        given:
        def yaml = """\
items:
  - summary: Task
    due: 2026-04-01
thresholds:
  age:
    green: 7
"""
        when:
        def result = TodoCreatedDateFiller.injectCreatedDates(yaml, TODAY)

        then:
        result.contains("created: ${TODAY_STR}")
        result.contains("thresholds:")
    }

    def "thresholds section before items does not interfere with injection"() {
        given:
        def yaml = """\
thresholds:
  due-in:
    green: 30
items:
  - summary: Task
"""
        when:
        def result = TodoCreatedDateFiller.injectCreatedDates(yaml, TODAY)

        then:
        result.contains("created: ${TODAY_STR}")
    }

    def "created nested inside a sub-key at deeper indent is not counted"() {
        given:
        // 'created' is at indent 6 (inside metadata:), not at indent 4 (direct item property)
        def yaml = """\
items:
  - summary: Task
    metadata:
      created: 2026-01-01
"""
        when:
        def result = TodoCreatedDateFiller.injectCreatedDates(yaml, TODAY)

        then:
        // A direct created: at indent 4 should have been added
        result.readLines().any { it == "    created: ${TODAY_STR}" }
    }

    def "crlf line endings are preserved after injection"() {
        given:
        def yaml = "items:\r\n  - summary: Task\r\n    due: 2026-04-01\r\n"

        when:
        def result = TodoCreatedDateFiller.injectCreatedDates(yaml, TODAY)

        then:
        result.contains("\r\n")
        result.contains("created: ${TODAY_STR}")
    }

    def "all items in a multi-item block without created dates all get injected"() {
        given:
        def yaml = """\
items:
  - summary: First
    due: 2026-04-01
  - summary: Second
  - summary: Third
    due: 2026-05-01
"""
        when:
        def result = TodoCreatedDateFiller.injectCreatedDates(yaml, TODAY)

        then:
        result.count("created: ${TODAY_STR}") == 3
    }
}