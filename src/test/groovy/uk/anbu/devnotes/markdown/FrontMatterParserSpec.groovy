package uk.anbu.devnotes.markdown

import spock.lang.Specification
import uk.anbu.devnotes.types.FrontMatter

class FrontMatterParserSpec extends Specification {

    // ─── FrontMatter.empty() / isEmpty() ────────────────────────────────────

    def "empty() produces a FrontMatter with no data and isEmpty() returns true"() {
        given:
        def fm = FrontMatter.empty()

        expect:
        fm.title() == null
        fm.tags().isEmpty()
        fm.description() == null
        fm.extra().isEmpty()
        fm.isEmpty()
    }

    def "FrontMatter with at least one field is not empty"() {
        expect:
        !FrontMatter.from([title: ["My Title"]]).isEmpty()
        !FrontMatter.from([tags: ["a", "b"]]).isEmpty()
        !FrontMatter.from([description: ["desc"]]).isEmpty()
        !FrontMatter.from([custom: ["value"]]).isEmpty()
    }

    // ─── FrontMatter.from(Map) ───────────────────────────────────────────────

    def "from(null) returns empty"() {
        expect:
        FrontMatter.from(null) == FrontMatter.empty()
    }

    def "from(empty map) returns empty"() {
        expect:
        FrontMatter.from([:]) == FrontMatter.empty()
    }

    def "from map with title only populates title, tags empty"() {
        given:
        def fm = FrontMatter.from([title: ["My Page"]])

        expect:
        fm.title() == "My Page"
        fm.tags().isEmpty()
        fm.description() == null
        fm.extra().isEmpty()
    }

    def "from map with tags populates tag list"() {
        given:
        def fm = FrontMatter.from([tags: ["java", "spring", "devnotes"]])

        expect:
        fm.tags() == ["java", "spring", "devnotes"]
        fm.title() == null
    }

    def "from map with description populates description"() {
        given:
        def fm = FrontMatter.from([description: ["A short description"]])

        expect:
        fm.description() == "A short description"
    }

    def "unknown keys land in extra and are not in title/tags/description"() {
        given:
        def fm = FrontMatter.from([author: ["Alice"], version: ["1.0"]])

        expect:
        fm.title() == null
        fm.extra() == [author: ["Alice"], version: ["1.0"]]
    }

    def "known keys are excluded from extra"() {
        given:
        def fm = FrontMatter.from([title: ["T"], tags: ["a"], description: ["D"], custom: ["x"]])

        expect:
        !fm.extra().containsKey("title")
        !fm.extra().containsKey("tags")
        !fm.extra().containsKey("description")
        fm.extra() == [custom: ["x"]]
    }

    def "only first title value is used when list has multiple entries"() {
        given:
        def fm = FrontMatter.from([title: ["First", "Second"]])

        expect:
        fm.title() == "First"
    }

    // ─── FrontMatterParser.parseText ─────────────────────────────────────────

    def "parseText on markdown without front-matter returns empty"() {
        given:
        def md = """\
# Hello

Some content.
"""
        expect:
        FrontMatterParser.parseText(md).isEmpty()
    }

    def "parseText extracts title from front-matter block"() {
        given:
        def md = """\
---
title: My Great Page
---

# Content
"""
        when:
        def fm = FrontMatterParser.parseText(md)

        then:
        fm.title() == "My Great Page"
        fm.tags().isEmpty()
    }

    def "parseText extracts tags list from front-matter block"() {
        given:
        def md = """\
---
tags:
  - java
  - spring
  - wiki
---

Content here.
"""
        when:
        def fm = FrontMatterParser.parseText(md)

        then:
        fm.tags() == ["java", "spring", "wiki"]
    }

    def "parseText extracts all named fields from front-matter"() {
        given:
        def md = """\
---
title: Full Page
description: A page with all fields
tags:
  - foo
  - bar
---

Body.
"""
        when:
        def fm = FrontMatterParser.parseText(md)

        then:
        fm.title() == "Full Page"
        fm.description() == "A page with all fields"
        fm.tags() == ["foo", "bar"]
        fm.extra().isEmpty()
    }

    def "parseText stores unknown front-matter keys in extra"() {
        given:
        def md = """\
---
author: Bob
version: 2.0
---

Content.
"""
        when:
        def fm = FrontMatterParser.parseText(md)

        then:
        fm.extra().containsKey("author")
        fm.extra().containsKey("version")
        fm.title() == null
    }

    def "parseText on empty string returns empty"() {
        expect:
        FrontMatterParser.parseText("").isEmpty()
    }

    def "parseText on markdown with only front-matter delimiters and no keys returns empty"() {
        given:
        def md = """\
---
---

Content.
"""
        expect:
        FrontMatterParser.parseText(md).isEmpty()
    }
}