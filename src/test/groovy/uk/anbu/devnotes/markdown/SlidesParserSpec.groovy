package uk.anbu.devnotes.markdown

import spock.lang.Specification
import uk.anbu.devnotes.markdown.slides.SlidesParser
import uk.anbu.devnotes.types.DeckMetadata
import uk.anbu.devnotes.types.FrontMatter
import uk.anbu.devnotes.types.SlideMetadata

class SlidesParserSpec extends Specification {

    // ─── DeckMetadata.from ───────────────────────────────────────────────────

    def "DeckMetadata.from reads theme, paginate, headingDivider, background, class and lang from FrontMatter extra"() {
        given:
        def fm = FrontMatter.from([
                type          : ["slides"],
                theme         : ["dark"],
                paginate      : ["true"],
                headingDivider: ["2"],
                background    : ["#111"],
                class         : ["center"],
                lang          : ["en"]
        ])

        when:
        def dm = DeckMetadata.from(fm)

        then:
        dm.theme() == "dark"
        dm.paginate()
        dm.headingDivider() == [2]
        dm.background() == "#111"
        dm.cssClass() == "center"
        dm.lang() == "en"
    }

    def "DeckMetadata.from ignores non-integer headingDivider values"() {
        given:
        def fm = FrontMatter.from([headingDivider: ["2", "notanumber", "3"]])

        when:
        def dm = DeckMetadata.from(fm)

        then:
        dm.headingDivider() == [2, 3]
    }

    // ─── SlideMetadata.tryParse ──────────────────────────────────────────────

    def "SlideMetadata.tryParse returns empty for blank input"() {
        expect:
        SlideMetadata.tryParse("").isEmpty()
        SlideMetadata.tryParse("   ").isEmpty()
        SlideMetadata.tryParse(null).isEmpty()
    }

    def "SlideMetadata.tryParse returns empty when no known fields are present"() {
        expect:
        SlideMetadata.tryParse("name: John\nage: 30").isEmpty()
    }

    def "SlideMetadata.tryParse returns empty for non-YAML content"() {
        expect:
        SlideMetadata.tryParse("## A heading\n\nSome paragraph.").isEmpty()
    }

    def "SlideMetadata.tryParse parses known fields successfully"() {
        given:
        def yaml = """\
layout: center
background: dark
notes: Explain this slide.
"""
        when:
        def result = SlideMetadata.tryParse(yaml)

        then:
        result.isPresent()
        result.get().layout() == "center"
        result.get().background() == "dark"
        result.get().notes() == "Explain this slide."
    }

    def "SlideMetadata.tryParse maps 'class' key to cssClass"() {
        when:
        def result = SlideMetadata.tryParse("class: highlight")

        then:
        result.isPresent()
        result.get().cssClass() == "highlight"
    }

    // ─── SlidesParser.stripFrontMatter ──────────────────────────────────────

    def "stripFrontMatter removes YAML front-matter block"() {
        given:
        def md = "---\ntitle: Test\ntype: slides\n---\n\n# Content"

        expect:
        SlidesParser.stripFrontMatter(md).trim() == "# Content"
    }

    def "stripFrontMatter is a no-op when no front-matter is present"() {
        given:
        def md = "# Content\n\nParagraph."

        expect:
        SlidesParser.stripFrontMatter(md) == md
    }

    // ─── SlidesParser.applyHeadingDivider ───────────────────────────────────

    def "applyHeadingDivider returns single element when levels is empty"() {
        given:
        def text = "## Section\n\nContent"

        expect:
        SlidesParser.applyHeadingDivider(text, []) == [text]
    }

    def "applyHeadingDivider splits before matching heading level"() {
        given:
        def text = "Intro text.\n\n## Section 1\n\nContent 1.\n\n## Section 2\n\nContent 2."

        when:
        def parts = SlidesParser.applyHeadingDivider(text, [2])

        then:
        parts.size() == 3
        parts[0].trim() == "Intro text."
        parts[1].trim().startsWith("## Section 1")
        parts[2].trim().startsWith("## Section 2")
    }

    def "applyHeadingDivider does not split on non-matching heading level"() {
        given:
        def text = "# H1\n\n## H2\n\n### H3"

        when:
        def parts = SlidesParser.applyHeadingDivider(text, [3])

        then:
        parts.size() == 2
        parts[0].trim() == "# H1\n\n## H2"
        parts[1].trim() == "### H3"
    }

    // ─── SlidesParser.extractRevealNotes ────────────────────────────────────

    def "extractRevealNotes returns empty string when no note: line present"() {
        expect:
        SlidesParser.extractRevealNotes("## Slide\n\nContent.") == ""
    }

    def "extractRevealNotes returns lines after note: marker"() {
        given:
        def content = "## Slide\n\nContent.\n\nnote:\nFirst note line.\nSecond note line."

        expect:
        SlidesParser.extractRevealNotes(content) == "First note line.\nSecond note line."
    }

    def "removeRevealNoteLines removes note: section from content"() {
        given:
        def content = "## Slide\n\nContent.\n\nnote:\nHidden."

        expect:
        SlidesParser.removeRevealNoteLines(content) == "## Slide\n\nContent.\n"
    }

    // ─── SlidesParser.parse ──────────────────────────────────────────────────

    def "parse returns empty deck for null or blank input"() {
        expect:
        SlidesParser.parse(null).slides().isEmpty()
        SlidesParser.parse("").slides().isEmpty()
        SlidesParser.parse("   ").slides().isEmpty()
    }

    def "parse returns single slide for document with no separator"() {
        given:
        def md = "---\ntype: slides\n---\n\n# Hello\n\nWorld."

        when:
        def deck = SlidesParser.parse(md)

        then:
        deck.slides().size() == 1
        deck.slides()[0].rawMarkdown().contains("# Hello")
    }

    def "parse splits on explicit --- separators"() {
        given:
        def md = """\
---
type: slides
---

# Slide 1

Content one.

---

# Slide 2

Content two.
"""
        when:
        def deck = SlidesParser.parse(md)

        then:
        deck.slides().size() == 2
        deck.slides()[0].rawMarkdown().contains("Slide 1")
        deck.slides()[1].rawMarkdown().contains("Slide 2")
    }

    def "parse does not treat document front-matter --- as a slide separator"() {
        given:
        def md = """\
---
title: My Deck
type: slides
---

# Only slide
"""
        when:
        def deck = SlidesParser.parse(md)

        then:
        deck.slides().size() == 1
    }

    def "parse recognises per-slide metadata block between consecutive --- separators"() {
        given:
        def md = """\
---
type: slides
---

# Slide 1

---
layout: center
background: dark
---

# Slide 2 with metadata
"""
        when:
        def deck = SlidesParser.parse(md)

        then:
        deck.slides().size() == 2
        deck.slides()[0].metadata() == SlideMetadata.empty()
        deck.slides()[1].metadata().layout() == "center"
        deck.slides()[1].metadata().background() == "dark"
    }

    def "parse treats malformed per-slide YAML as slide content"() {
        given:
        def md = """\
---
type: slides
---

# Slide 1

---

name: John
age: thirty-not-a-known-field

# Slide 2
"""
        when:
        def deck = SlidesParser.parse(md)

        // The unknown-field-only block is treated as slide content, not metadata
        then:
        deck.slides().size() == 2
    }

    def "parse applies headingDivider to split slides on H2 headings"() {
        given:
        def md = """\
---
type: slides
headingDivider: 2
---

# Title

Intro.

## Section A

Content A.

## Section B

Content B.
"""
        when:
        def deck = SlidesParser.parse(md)

        then:
        deck.slides().size() == 3
        deck.slides()[0].rawMarkdown().trim() == "# Title\n\nIntro."
        deck.slides()[1].rawMarkdown().contains("Section A")
        deck.slides()[2].rawMarkdown().contains("Section B")
    }

    def "parse extracts note: lines as speaker notes"() {
        given:
        def md = """\
---
type: slides
---

# Slide

Content.

note:
This is a speaker note.
"""
        when:
        def deck = SlidesParser.parse(md)

        then:
        deck.slides().size() == 1
        deck.slides()[0].speakerNotes() == "This is a speaker note."
        !deck.slides()[0].rawMarkdown().contains("note:")
    }

    def "parse uses notes field from per-slide metadata"() {
        given:
        def md = """\
---
type: slides
---

---
notes: Meta note text.
---

# Slide with notes
"""
        when:
        def deck = SlidesParser.parse(md)

        then:
        deck.slides().size() == 1
        deck.slides()[0].speakerNotes() == "Meta note text."
    }

    def "parse combines per-slide metadata notes and reveal-style note lines"() {
        given:
        def md = """\
---
type: slides
---

---
notes: From metadata.
---

# Slide

Content.

note:
From reveal section.
"""
        when:
        def deck = SlidesParser.parse(md)

        then:
        deck.slides().size() == 1
        deck.slides()[0].speakerNotes().contains("From metadata.")
        deck.slides()[0].speakerNotes().contains("From reveal section.")
    }

    def "parse canonical example from plan produces correct deck"() {
        given:
        def md = """\
---
type: slides
title: Distributed Systems Notes
theme: default
paginate: true
headingDivider: 2
---

# Distributed Systems

A practical overview.

---
layout: center
background: dark
notes: Explain why failure is the default assumption.
---

## Fault tolerance

- Partial failure is normal
- Timeouts are ambiguous
- Retries need idempotency

note:
Mention TCP vs application-level retry behavior.
"""
        when:
        def deck = SlidesParser.parse(md)

        then:
        deck.deckMetadata().theme() == "default"
        deck.deckMetadata().paginate()
        deck.deckMetadata().headingDivider() == [2]
        deck.slides().size() == 2
        deck.slides()[0].rawMarkdown().contains("Distributed Systems")
        deck.slides()[1].metadata().layout() == "center"
        deck.slides()[1].speakerNotes().contains("Explain why failure")
        deck.slides()[1].speakerNotes().contains("Mention TCP")
        !deck.slides()[1].rawMarkdown().contains("note:")
    }
}