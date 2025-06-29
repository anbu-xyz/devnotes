package uk.anbu.devnotes.module

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class SearchLocationExecutorSpec extends Specification {
    @TempDir
    Path tempDir

    def "should find matching files based on search terms"() {
        given:
        def file1 = createFile("docs/test-file1.md")
        def file2 = createFile("docs/subfolder/test-file2.md")
        def file3 = createFile("docs/another-file.md")

        def searchParam = "test file"
        def executor = new SearchLocationExecutor(tempDir, searchParam)

        when:
        def results = executor.getResult()

        then:
        results.results().size() == 2
        results.results().collect { it.fileName() }.sort() == [
                "docs/test-file1.md",
                "docs/subfolder/test-file2.md"
        ].sort()
    }

    def "should return empty list when no matches found"() {
        given:
        def file1 = createFile("docs/abc.md")
        def file2 = createFile("docs/def.md")

        def searchParam = "xyz"
        def executor = new SearchLocationExecutor(tempDir, searchParam)

        when:
        def results = executor.getResult()

        then:
        results.results().isEmpty()
    }

    def "should match terms in order"() {
        given:
        def file1 = createFile("docs/test-file-example.md")
        def file2 = createFile("docs/file-test-example.md") // Wrong order

        def searchParam = "test file"
        def executor = new SearchLocationExecutor(tempDir, searchParam)

        when:
        def results = executor.getResult()

        then:
        results.results().size() == 1
        results.results()[0].fileName() == "docs/test-file-example.md"
    }

    def "should handle case insensitive search"() {
        given:
        def file1 = createFile("docs/TEST-FILE.md")

        def searchParam = "test file"
        def executor = new SearchLocationExecutor(tempDir, searchParam)

        when:
        def results = executor.getResult()

        then:
        results.results().size() == 1
        results.results()[0].fileName() == "docs/TEST-FILE.md"
    }

    private Path createFile(String path) {
        def file = tempDir.resolve(path)
        Files.createDirectories(file.parent)
        Files.createFile(file)
        return file
    }
}
