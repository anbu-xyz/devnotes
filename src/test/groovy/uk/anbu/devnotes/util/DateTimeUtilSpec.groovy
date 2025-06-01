package uk.anbu.devnotes.util

import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

class DateTimeUtilSpec extends Specification {

    @Unroll
    def "toHumanReadable returns '#expected' for duration of #input"() {
        expect:
        DateTimeUtil.toHumanReadable(Duration.ofSeconds(input)) == expected

        where:
        input    | expected
        0        | "just now"
        30       | "just now"
        120      | "2 minutes ago"
        60       | "1 minute ago"
        1800     | "30 minutes ago"
        1900     | "about 30 mins ago"
        2800     | "about an hour ago"
        3600     | "an hour ago"
        7200     | "2 hours ago"
        86400    | "yesterday"
        172800   | "2 days ago"
        604800   | "a week ago"
        1209600  | "2 weeks ago"
        2419200  | "a month ago"
        5184000  | "2 months ago"
        31536000 | "12 months ago"
        63072000 | "2 years ago"
    }

    def "toHumanReadable handles edge cases"() {
        expect:
        DateTimeUtil.toHumanReadable(Duration.ZERO) == "just now"
        DateTimeUtil.toHumanReadable(Duration.ofMinutes(46)) == "about an hour ago"
        DateTimeUtil.toHumanReadable(Duration.ofDays(28)) == "a month ago"
        DateTimeUtil.toHumanReadable(Duration.ofDays(59)) == "a month ago"
    }
}