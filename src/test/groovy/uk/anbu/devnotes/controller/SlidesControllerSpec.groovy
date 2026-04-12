package uk.anbu.devnotes.controller

import gg.jte.ContentType
import gg.jte.TemplateEngine
import gg.jte.resolve.DirectoryCodeResolver
import org.jsoup.Jsoup
import org.springframework.http.HttpStatus
import spock.lang.Specification
import uk.anbu.devnotes.markdown.code.DatabaseMetadataBlockTranslator
import uk.anbu.devnotes.module.GroovyRenderer
import uk.anbu.devnotes.module.MarkdownRenderer
import uk.anbu.devnotes.module.SlidesRenderer
import uk.anbu.devnotes.service.ConfigService

import java.nio.file.Files
import java.nio.file.Paths

class SlidesControllerSpec extends Specification {

    MarkdownController controller
    MarkdownRenderer markdownRenderer
    TemplateEngine templateEngine
    ConfigService configService

    def setup() {
        def groovyRenderer = new GroovyRenderer((r) -> Optional.empty())
        def dataSourceConfigResolver = { x -> new ConfigService.DataSourceConfig("testDB", "jdbc:test:url", "u", "p") }
        markdownRenderer = new MarkdownRenderer(groovyRenderer, dataSourceConfigResolver, null, new DatabaseMetadataBlockTranslator(), null)
        def codeResolver = new DirectoryCodeResolver(Paths.get("src/main/jte"))
        templateEngine = TemplateEngine.create(codeResolver, Paths.get("src/main/jte"), ContentType.Html)
        configService = Mock(ConfigService)
        controller = new MarkdownController(markdownRenderer, templateEngine, configService, new SlidesRenderer(markdownRenderer))
    }

    def "markdownViewer renders slides-viewer template for type:slides front-matter"() {
        given:
        def tempFile = Files.createTempFile("slides-", ".md")
        configService.getDocsDirectory() >> tempFile.parent.toAbsolutePath().toString()
        Files.writeString(tempFile, """\
---
type: slides
title: My Deck
---

# Slide One

Content here.
""")
        when:
        def response = controller.markdownViewer(tempFile.fileName.toString(), [:])

        then:
        response.statusCode == HttpStatus.OK
        def doc = Jsoup.parse(response.body.toString())
        doc.select(".slide-deck").size() == 1
        doc.select(".slide").size() == 1
        doc.select("#slidesContainer").size() == 1

        cleanup:
        Files.deleteIfExists(tempFile)
    }

    def "markdownViewer produces correct slide count for multi-slide deck"() {
        given:
        def tempFile = Files.createTempFile("slides-", ".md")
        configService.getDocsDirectory() >> tempFile.parent.toAbsolutePath().toString()
        Files.writeString(tempFile, """\
---
type: slides
---

# Slide One

---

# Slide Two

---

# Slide Three
""")
        when:
        def response = controller.markdownViewer(tempFile.fileName.toString(), [:])

        then:
        response.statusCode == HttpStatus.OK
        def doc = Jsoup.parse(response.body.toString())
        doc.select(".slide").size() == 3
        doc.select(".slide-counter").text() == "1 / 3"

        cleanup:
        Files.deleteIfExists(tempFile)
    }

    def "markdownViewer uses front-matter title for type:slides"() {
        given:
        def tempFile = Files.createTempFile("slides-", ".md")
        configService.getDocsDirectory() >> tempFile.parent.toAbsolutePath().toString()
        Files.writeString(tempFile, """\
---
type: slides
title: Presentation Title
---

# Slide One
""")
        when:
        def response = controller.markdownViewer(tempFile.fileName.toString(), [:])

        then:
        response.statusCode == HttpStatus.OK
        Jsoup.parse(response.body.toString()).title() == "Presentation Title"

        cleanup:
        Files.deleteIfExists(tempFile)
    }

    def "fetchMarkdownContent renders slides.jte shell for type:slides front-matter"() {
        given:
        def tempFile = Files.createTempFile("slides-", ".md")
        configService.getDocsDirectory() >> tempFile.parent.toAbsolutePath().toString()
        Files.writeString(tempFile, """\
---
type: slides
title: Shell Test
---

# Slide
""")
        when:
        def result = controller.fetchMarkdownContent(
                tempFile.fileName.toString(), tempFile.parent, false, [:])

        then:
        def doc = Jsoup.parse(result.content())
        // slides.jte uses a different hidden trigger button id
        doc.select("#hiddenSlidesButton").size() == 1
        doc.select("#hiddenViewButton").size() == 0
        doc.title() == "Shell Test"

        cleanup:
        Files.deleteIfExists(tempFile)
    }

    def "markdownViewer renders per-slide metadata classes onto section elements"() {
        given:
        def tempFile = Files.createTempFile("slides-", ".md")
        configService.getDocsDirectory() >> tempFile.parent.toAbsolutePath().toString()
        Files.writeString(tempFile, """\
---
type: slides
---

# Slide 1

---
class: highlight
---

# Slide 2
""")
        when:
        def response = controller.markdownViewer(tempFile.fileName.toString(), [:])

        then:
        response.statusCode == HttpStatus.OK
        def doc = Jsoup.parse(response.body.toString())
        def slide2 = doc.select(".slide")[1]
        slide2.hasClass("highlight")

        cleanup:
        Files.deleteIfExists(tempFile)
    }

    def "markdownViewer renders speaker notes in aside.slide-notes element"() {
        given:
        def tempFile = Files.createTempFile("slides-", ".md")
        configService.getDocsDirectory() >> tempFile.parent.toAbsolutePath().toString()
        Files.writeString(tempFile, """\
---
type: slides
---

# Slide

Content.

note:
This is hidden.
""")
        when:
        def response = controller.markdownViewer(tempFile.fileName.toString(), [:])

        then:
        response.statusCode == HttpStatus.OK
        def doc = Jsoup.parse(response.body.toString())
        doc.select(".slide-notes").text() == "This is hidden."
        !doc.select(".slide-content").text().contains("note:")

        cleanup:
        Files.deleteIfExists(tempFile)
    }

    def "markdownViewer renders mermaid code fence as .mermaid div inside slide"() {
        given:
        def tempFile = Files.createTempFile("slides-", ".md")
        configService.getDocsDirectory() >> tempFile.parent.toAbsolutePath().toString()
        Files.writeString(tempFile, """\
---
type: slides
---

# Diagram Slide

```mermaid
graph TD
    A[Start] --> B[End]
```
""")
        when:
        def response = controller.markdownViewer(tempFile.fileName.toString(), [:])

        then:
        response.statusCode == HttpStatus.OK
        def doc = Jsoup.parse(response.body.toString())
        def mermaidDiv = doc.select(".slide .mermaid")
        mermaidDiv.size() == 1
        mermaidDiv.text().contains("graph TD")

        cleanup:
        Files.deleteIfExists(tempFile)
    }

    def "fetchMarkdownContent renders markdown.jte editor shell when editMode=true for a slides file"() {
        given:
        def tempFile = Files.createTempFile("slides-", ".md")
        configService.getDocsDirectory() >> tempFile.parent.toAbsolutePath().toString()
        Files.writeString(tempFile, """\
---
type: slides
title: My Deck
---

# Slide
""")
        when:
        def result = controller.fetchMarkdownContent(
                tempFile.fileName.toString(), tempFile.parent, true, [:])

        then:
        def doc = Jsoup.parse(result.content())
        doc.select("#hiddenEditButton").size() == 1
        doc.select("#hiddenSlidesButton").size() == 0

        cleanup:
        Files.deleteIfExists(tempFile)
    }
}

