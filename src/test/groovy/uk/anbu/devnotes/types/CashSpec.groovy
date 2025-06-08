package uk.anbu.devnotes.types

import spock.lang.Specification

class CashSpec extends Specification {
    def "cash can be created from currency and amount"() {
        when:
        var cash = Cash.from("USD", new BigDecimal(100))

        then:
        cash.amount == new BigDecimal(100)
        cash.currency == "USD"
    }

    def "cash can be created from currency and amount string"() {
        when:
        var cash = Cash.from("USD 100")

        then:
        cash.amount == new BigDecimal(100)
        cash.currency == "USD"
    }

    def "cash can be multiplied by a double"() {
        when:
        var cash = Cash.from("USD 100").times(2)

        then:
        cash.amount == new BigDecimal(200)
        cash.currency == "USD"
    }

    def "cash can be added to another cash"() {
        when:
        var cash = Cash.from("USD 100").plus(Cash.from("USD 200"))

        then:
        cash.amount == new BigDecimal(300)
        cash.currency == "USD"
    }

    def "cash can be subtracted from another cash"() {
        when:
        var cash = Cash.from("USD 200").minus(Cash.from("USD 100"))

        then:
        cash.amount == new BigDecimal(100)
        cash.currency == "USD"
    }

    def "cash cannot be added to another cash with different currency"() {
        when:
        var cash = Cash.from("USD 100").plus(Cash.from("EUR 200"))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Currencies must be the same"
    }

    def "cash cannot be subtracted from another cash with different currency"() {
        when:
        var cash = Cash.from("USD 100").minus(Cash.from("EUR 200"))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Currencies must be the same"
    }

    def "cash cannot be multiplied by a negative number"() {
        when:
        var cash = Cash.from("USD 100").times(-1)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Amount cannot be negative"
    }
}
