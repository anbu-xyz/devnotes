package uk.anbu.devnotes.util

import spock.lang.Specification

class FileUtilSpec extends Specification {

    def "cleanDirectoryName"() {
        expect:
        FileUtil.cleanDirectoryName(input) == output

        where:
        input               | output
        "a/b/c"             | "a/b/c"
        "a/b/c/"            | "a/b/c"
        "a/b/c/.."          | "a/b"
        "a/b/c/../.."       | "a"
        "a/b/c/../../"      | "a"
        "../a/b/c"          | "a/b/c"
        "../a/b/c/"         | "a/b/c"
        "../a/b/c/.."       | "a/b"
        "../a/b/c/../.."    | "a"
        "../../a/b/c"       | "a/b/c"
        "../../a/b/c/"      | "a/b/c"
        "../../a/b/c/.."    | "a/b"
        "../../a/b/c/../.." | "a"
        "/a/b/c"            | "a/b/c"
        "/a/b/c/"           | "a/b/c"
        "//a/b"             | "a/b"
    }
}
