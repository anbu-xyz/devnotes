package uk.anbu.devnotes.markdown

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class TodoModifierSpec extends Specification {

    @TempDir
    Path tempDir

    def "should create new todo file with fleeting item when file doesn't exist"() {
        given:
        def todoFile = tempDir.resolve("todo.md")
        def content = "Test fleeting note"

        when:
        TodoModifier.addFleetingItem(todoFile, content)

        then:
        todoFile.toFile().exists()
        def fileContent = todoFile.toFile().text
        fileContent.contains("# Todo")
        fileContent.contains("## Fleeting")
        fileContent.contains("- Test fleeting note")
    }

    def "should add fleeting item under Fleeting section in existing file"() {
        given:
        def todoFile = tempDir.resolve("todo.md")
        def initialContent = """
                # Todo
                
                ## Fleeting
                
                - existing item
                
                ## Other Section
                
                - other item
        """.split("\n").collect { it.trim() }.join("\n")
        todoFile.toFile() << initialContent

        when:
        TodoModifier.addFleetingItem(todoFile, "New fleeting note")

        then:
        def fileContent = todoFile.toFile().text
        fileContent.contains("# Todo")
        fileContent.contains("## Fleeting")
        fileContent.contains("- existing item")
        fileContent.contains("- New fleeting note")
        fileContent.contains("## Other Section")
        fileContent.contains("- other item")
    }

    def "should add fleeting item when there is no bullet list under Fleeting heading"() {
        given:
        def todoFile = tempDir.resolve("todo.md")
        def initialContent = """
            # Todo
            
            ## Fleeting
            
            some text
            
            ## Other Section
            
            - other item
        """.split("\n").collect { it.trim() }.join("\n")
        todoFile.toFile() << initialContent

        when:
        TodoModifier.addFleetingItem(todoFile, "New fleeting note")

        then:
        def fileContent = todoFile.toFile().text
        fileContent.contains("# Todo")
        fileContent.contains("## Fleeting")
        fileContent.contains("- New fleeting note")
        fileContent.contains("## Other Section")
        fileContent.contains("- other item")
    }

    def "should add fleeting item when there is no Fleeting heading"() {
        given:
        def todoFile = tempDir.resolve("todo.md")
        def initialContent = """
            # Todo
            
            ## Other Section
            
            - other item
        """.split("\n").collect { it.trim() }.join("\n")
        todoFile.toFile() << initialContent

        when:
        TodoModifier.addFleetingItem(todoFile, "New fleeting note")

        then:
        def fileContent = todoFile.toFile().text
        fileContent.contains("# Todo")
        fileContent.contains("## Fleeting")
        fileContent.contains("- New fleeting note")
        fileContent.contains("## Other Section")
        fileContent.contains("- other item")
    }

    def "should handle empty content gracefully"() {
        given:
        def todoFile = tempDir.resolve("todo.md")
        def content = "   "  // whitespace only

        when:
        TodoModifier.addFleetingItem(todoFile, content)

        then:
        todoFile.toFile().exists()
        def fileContent = todoFile.toFile().text
        fileContent.contains("# Todo")
        fileContent.contains("## Fleeting")
    }

    def "should throw RuntimeException for IO errors"() {
        given:
        def todoFile = tempDir.resolve("nonexistent-dir/todo")
        // create directory to force IO error
        todoFile.toFile().mkdirs()

        when:
        TodoModifier.addFleetingItem(todoFile, "Test content")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Error processing markdown file")

        cleanup:
        tempDir.toFile().setWritable(true)
    }
}
