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
        def file1 = createFile("docs/test-file1.txt")
        def file2 = createFile("docs/subfolder/test-file2.txt")
        def file3 = createFile("docs/another-file.txt")

        def searchParam = "test file"
        def executor = new SearchLocationExecutor(tempDir, searchParam)

        when:
        def results = executor.getResult()

        then:
        results.size() == 2
        results.collect { it.fileName() }.sort() == [
                "docs/test-file1.txt",
                "docs/subfolder/test-file2.txt"
        ].sort()
    }

    def "should return empty list when no matches found"() {
        given:
        def file1 = createFile("docs/abc.txt")
        def file2 = createFile("docs/def.txt")

        def searchParam = "xyz"
        def executor = new SearchLocationExecutor(tempDir, searchParam)

        when:
        def results = executor.getResult()

        then:
        results.isEmpty()
    }

    def "should match terms in order"() {
        given:
        def file1 = createFile("docs/test-file-example.txt")
        def file2 = createFile("docs/file-test-example.txt") // Wrong order

        def searchParam = "test file"
        def executor = new SearchLocationExecutor(tempDir, searchParam)

        when:
        def results = executor.getResult()

        then:
        results.size() == 1
        results[0].fileName() == "docs/test-file-example.txt"
    }

    def "should handle case insensitive search"() {
        given:
        def file1 = createFile("docs/TEST-FILE.txt")

        def searchParam = "test file"
        def executor = new SearchLocationExecutor(tempDir, searchParam)

        when:
        def results = executor.getResult()

        then:
        results.size() == 1
        results[0].fileName() == "docs/TEST-FILE.txt"
    }

    private Path createFile(String path) {
        def file = tempDir.resolve(path)
        Files.createDirectories(file.parent)
        Files.createFile(file)
        return file
    }
}
