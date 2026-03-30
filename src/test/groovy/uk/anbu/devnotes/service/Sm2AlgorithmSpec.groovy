package uk.anbu.devnotes.service

import spock.lang.Specification
import spock.lang.Unroll
import uk.anbu.devnotes.types.FlashCard

import java.time.LocalDateTime

import static java.time.ZoneOffset.UTC

class Sm2AlgorithmSpec extends Specification {

    // ---- helpers ----

    /** Creates a FlashCard with sensible defaults; override any field via named args. */
    private static FlashCard newCard(Map args = [:]) {
        def card = new FlashCard()
        card.reviewCount   = (args.reviewCount   ?: 0)   as int
        card.interval      = (args.interval      ?: 1)   as int
        card.easeFactor    = args.containsKey('easeFactor') ? (args.easeFactor as double) : 2.5d
        card.correctCount  = (args.correctCount  ?: 0)   as int
        card.incorrectCount = (args.incorrectCount ?: 0) as int
        return card
    }

    // ---- quality 5: perfect recall on a brand-new card ----

    def "quality 5 (perfect) on new card — interval=1, reviewCount=1, correctCount=1, EF increases"() {
        given:
        def card = newCard()
        def before = LocalDateTime.now(UTC)

        when:
        def result = Sm2Algorithm.apply(card, 5)

        then:
        result.interval == 1
        result.reviewCount == 1
        result.correctCount == 1
        result.incorrectCount == 0
        result.easeFactor > 2.5d
        result.lastReviewed >= before
        result.lastReviewed <= LocalDateTime.now(UTC)
        result.nextReview == result.lastReviewed.plusDays(1)
    }

    // ---- quality 4 on reviewCount=1: interval jumps to 6 ----

    def "quality 4 on reviewCount=1 card — interval becomes 6"() {
        given:
        def card = newCard(reviewCount: 1, interval: 1)

        when:
        def result = Sm2Algorithm.apply(card, 4)

        then:
        result.interval == 6
        result.reviewCount == 2
        result.correctCount == 1
    }

    // ---- quality 4 on reviewCount >= 2: interval = round(interval * EF) ----

    def "quality 4 on reviewCount >= 2 — interval = round(prevInterval * prevEF)"() {
        given:
        def card = newCard(reviewCount: 3, interval: 6, easeFactor: 2.5)

        when:
        def prevInterval = card.interval   // 6
        def prevEF       = card.easeFactor // 2.5
        def result = Sm2Algorithm.apply(card, 4)

        then:
        result.interval == Math.round(prevInterval * prevEF) as int   // 15
        result.reviewCount == 4
    }

    // ---- quality 3: minimum correct rating — EF floor ----

    def "quality 3 (minimum pass) starting at EF=1.3 — EF never drops below 1.3"() {
        given:
        def card = newCard(easeFactor: 1.3)

        when:
        def result = Sm2Algorithm.apply(card, 3)

        then:
        result.easeFactor >= 1.3d
        result.reviewCount == 1
        result.correctCount == 1
        result.incorrectCount == 0
    }

    // ---- quality 2: failed recall ----

    def "quality 2 (fail) — interval=1, reviewCount=0, incorrectCount++, history preserved"() {
        given:
        def card = newCard(reviewCount: 5, interval: 30, correctCount: 5, easeFactor: 2.5)

        when:
        def result = Sm2Algorithm.apply(card, 2)

        then:
        result.interval == 1
        result.reviewCount == 0               // streak reset
        result.incorrectCount == 1
        result.correctCount == 5              // cumulative history preserved
        result.easeFactor == 2.5d             // EF unchanged on failure
    }

    // ---- quality 0: blackout ----

    def "quality 0 (blackout) — EF stays >= 1.3, interval=1, reviewCount=0"() {
        given:
        def card = newCard(easeFactor: 1.3)

        when:
        def result = Sm2Algorithm.apply(card, 0)

        then:
        result.easeFactor >= 1.3d             // floor holds
        result.interval == 1
        result.reviewCount == 0
        result.incorrectCount == 1
    }

    // ---- successive reviews compound correctly ----

    def "successive quality=4 reviews compound interval growth over three sessions"() {
        given:
        def card = newCard()

        when: "1st review — reviewCount 0→1, interval stays 1"
        card = Sm2Algorithm.apply(card, 4)

        then:
        card.interval == 1
        card.reviewCount == 1

        when: "2nd review — reviewCount 1→2, interval jumps to 6"
        card = Sm2Algorithm.apply(card, 4)

        then:
        card.interval == 6
        card.reviewCount == 2

        when: "3rd review — reviewCount 2→3, interval = round(6 * EF)"
        def prevInterval = card.interval   // 6
        def prevEF       = card.easeFactor
        card = Sm2Algorithm.apply(card, 4)

        then:
        card.interval == Math.round(prevInterval * prevEF) as int
        card.reviewCount == 3
    }

    // ---- timestamps ----

    def "lastReviewed is set close to now; nextReview = lastReviewed + interval days"() {
        given:
        def card = newCard()
        def before = LocalDateTime.now(UTC)

        when:
        def result = Sm2Algorithm.apply(card, 4)

        then:
        result.lastReviewed >= before
        result.lastReviewed <= LocalDateTime.now(UTC)
        result.nextReview == result.lastReviewed.plusDays(result.interval)
    }

    // ---- invalid quality ----

    @Unroll
    def "quality #quality out of range [0,5] throws IllegalArgumentException"() {
        given:
        def card = newCard()

        when:
        Sm2Algorithm.apply(card, quality)

        then:
        thrown(IllegalArgumentException)

        where:
        quality | _
        -1      | _
        6       | _
        100     | _
        -100    | _
    }

    // ---- immutability of input ----

    def "input card is never mutated — original fields unchanged after apply"() {
        given:
        def original = newCard(reviewCount: 3, interval: 10, easeFactor: 2.5)

        when:
        Sm2Algorithm.apply(original, 5)

        then:
        original.reviewCount == 3
        original.interval == 10
        original.easeFactor == 2.5d
        original.lastReviewed == null
        original.nextReview == null
    }

    // ---- EF formula spot-checks ----

    def "EF formula: quality=5 increases EF by 0.1"() {
        given:
        def card = newCard(easeFactor: 2.5)

        when:
        def result = Sm2Algorithm.apply(card, 5)

        then:
        // EF = 2.5 + 0.1 - (5-5)*(0.08 + 0*0.02) = 2.6
        Math.abs(result.easeFactor - 2.6d) < 0.0001d
    }

    def "EF formula: quality=4 leaves EF unchanged"() {
        given:
        def card = newCard(easeFactor: 2.5)

        when:
        def result = Sm2Algorithm.apply(card, 4)

        then:
        // EF = 2.5 + 0.1 - 1*(0.08 + 0.02) = 2.5 + 0.1 - 0.1 = 2.5
        Math.abs(result.easeFactor - 2.5d) < 0.0001d
    }

    def "EF formula: quality=3 decreases EF to approximately 2.36"() {
        given:
        def card = newCard(easeFactor: 2.5)

        when:
        def result = Sm2Algorithm.apply(card, 3)

        then:
        // EF = 2.5 + 0.1 - 2*(0.08 + 2*0.02) = 2.5 + 0.1 - 0.24 = 2.36
        Math.abs(result.easeFactor - 2.36d) < 0.0001d
    }
}

