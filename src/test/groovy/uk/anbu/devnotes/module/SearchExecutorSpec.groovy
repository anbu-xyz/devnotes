package uk.anbu.devnotes.module

import spock.lang.Specification
import spock.lang.TempDir

class SearchExecutorSpec extends Specification {
    @TempDir
    File tempDir

    SearchExecutor searchExecutor

    def setup() {
        searchExecutor = new SearchExecutor(tempDir.absolutePath)
    }

    def "should find matches in files case sensitive"() {
        given:
        createTestFile("test1.md", "This is a test file with some Content")
        createTestFile("test2.md", "Another file with different content")

        when:
        def results = searchExecutor.search("Content", true)

        then:
        results.results().size() == 1
        results.results()[0].fileName == "test1.md"
        results.results()[0].preview.contains("Content")
    }

    def "should correctly find files in subdirectories"() {
        given:
        new File(tempDir, "subdir").mkdir()
        createTestFile(new File(tempDir, "subdir/test3.md"), "This is a test file with some Content")

        when:
        def results = searchExecutor.search("Content", true)

        then:
        results.results().size() == 1
        results.results()*.fileName == ["subdir/test3.md"]
    }

    def "should find matches in files case insensitive"() {
        given:
        createTestFile("test1.md", "This is a test file with some Content")
        createTestFile("test2.md", "Another file with different content")

        when:
        def results = searchExecutor.search("content", false)

        then:
        results.results().size() == 2
        results.results()*.fileName.sort() == ["test1.md", "test2.md"]
        results.results().every { it.preview.toLowerCase().contains("content") }
    }

    def "should find matches for multiple words"() {
        given:
        createTestFile("test1.md", "This is a test file")
        createTestFile("test2.md", "Another test document")

        when:
        def results = searchExecutor.search("test file", false)

        then:
        results.results().size() == 1
        results.results()[0].fileName == "test1.md"
        results.results()[0].preview.contains("test file")
    }

    def "should return empty list when no matches found"() {
        given:
        createTestFile("test1.md", "This is a test file")

        when:
        def results = searchExecutor.search("nonexistent", false)

        then:
        results.results().isEmpty()
    }

    def "should include context in preview"() {
        given:
        createTestFile("test1.md", "A" * 200 + "target" + "B" * 200)

        when:
        def results = searchExecutor.search("target", true)

        then:
        results.results().size() == 1
        def preview = results.results()[0].preview
        preview.length() > 200
        preview.contains("target")
        preview.endsWith("...\n")
    }

    def "should handle empty directory"() {
        when:
        def results = searchExecutor.search("test", false)

        then:
        results.results().isEmpty()
    }

    def "should throw IOException for invalid directory"() {
        given:
        searchExecutor = new SearchExecutor("/nonexistent/path")

        when:
        searchExecutor.search("test", false)

        then:
        thrown(IOException)
    }

    private void createTestFile(String fileName, String content) {
        new File(tempDir, fileName).text = content
    }

    private void createTestFile(File file, String content) {
        file.parentFile.mkdirs()
        file.text = content
    }
}