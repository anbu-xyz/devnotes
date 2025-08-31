package uk.anbu.devnotes.markdown.image

import org.commonmark.node.Image
import org.commonmark.node.Paragraph
import org.commonmark.node.Text
import spock.lang.Specification
import spock.lang.TempDir
import uk.anbu.devnotes.types.MarkdownFile

import java.nio.charset.StandardCharsets
import java.nio.file.Path

class LocalImageTransformerSpec extends Specification {

    @TempDir
    Path tempDir

    def "should transform local image paths correctly"() {
        given:
        def markdownFile = new MarkdownFile(tempDir, fileName)
        def transformer = LocalImageTransformer.builder()
                .markdownFile(markdownFile)
                .build()
        def image = new Image()
        image.setDestination(imagePath)
        def root = new Paragraph()
        root.appendChild(image)

        when:
        transformer.transform(root)

        then:
        def destination = image.getDestination()
        def urlDecodedDestination = URLDecoder.decode(destination, StandardCharsets.UTF_8)
        urlDecodedDestination == expectedPath

        where:
        fileName        | imagePath                   | expectedPath
        "test.md"       | "http://example.com/image"  | "http://example.com/image"
        "test.md"       | "https://example.com/image" | "https://example.com/image"
        "test.md"       | "image.png"                 | "/image?filename=image.png"
        "/docs/test.md" | "image.png"                 | "/image?filename=/docs/image.png"
        "docs/test.md"  | "image.png"                 | "/image?filename=docs/image.png"
        "test.md"       | "/absolute/img.png"         | "/image?filename=/absolute/img.png"
        "docs\\test.md" | "image.png"                 | "/image?filename=docs/image.png"  // Windows path
    }

    def "should handle plantuml content urls"() {
        given:
        def markdownFile = new MarkdownFile(tempDir, "some-file.md")
        def transformer = LocalImageTransformer.builder()
                .markdownFile(markdownFile)
                .build()
        def image = new Image()
        image.setDestination("/plantumlContent?data=ABC123")
        def root = new Paragraph()
        root.appendChild(image)

        when:
        transformer.transform(root)

        then:
        image.getDestination() == "/plantumlContent?data=ABC123"
    }

    def "should handle special characters in image paths"() {
        given:
        def markdownFile = new MarkdownFile(tempDir, "some-file.md")
        def transformer = LocalImageTransformer.builder()
                .markdownFile(markdownFile)
                .build()
        def image = new Image()
        image.setDestination("image with spaces.png")
        def root = new Paragraph()
        root.appendChild(image)

        when:
        transformer.transform(root)

        then:
        def destination = image.getDestination()
        def urlDecodedDestination = URLDecoder.decode(destination, StandardCharsets.UTF_8)
        urlDecodedDestination == "/image?filename=image with spaces.png"
    }

    def "should traverse nested nodes"() {
        given:
        def markdownFile = new MarkdownFile(tempDir, "some-file.md")
        def transformer = LocalImageTransformer.builder()
                .markdownFile(markdownFile)
                .build()

        // Create a nested structure
        def root = new Paragraph()
        def text = new Text("text")
        def image1 = new Image()
        def image2 = new Image()
        image1.setDestination("image1.png")
        image2.setDestination("image2.png")

        root.appendChild(text)
        root.appendChild(image1)
        text.insertAfter(image2)

        when:
        transformer.transform(root)

        then:
        image1.getDestination() == "/image?filename=image1.png"
        image2.getDestination() == "/image?filename=image2.png"
    }

    def "should handle null or empty paths gracefully"() {
        given:
        def markdownFile = new MarkdownFile(tempDir, fileName)
        def transformer = LocalImageTransformer.builder()
                .markdownFile(markdownFile)
                .build()
        def image = new Image()
        image.setDestination(imagePath)
        def root = new Paragraph()
        root.appendChild(image)

        when:
        transformer.transform(root)

        then:
        image.getDestination() == expectedPath

        where:
        fileName  | imagePath | expectedPath
        ""        | "img.png" | "/image?filename=img.png"
        null      | "img.png" | "/image?filename=img.png"
        "test.md" | ""        | "/image?filename="
    }
}