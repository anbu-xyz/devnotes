package uk.anbu.devnotes.cash

import spock.lang.Specification
import uk.anbu.devnotes.types.CurrencyPair

class ExchangeRatesSpec extends Specification {

    def setup() {
        ExchangeRates.clear()
    }

    def "should return direct exchange rate when available"() {
        given:
        def pair = new CurrencyPair("USD", "EUR")
        ExchangeRates.setExchangeRate(pair, 0.85)

        when:
        def result = ExchangeRates.getExchangeRate(pair)

        then:
        result.isPresent()
        result.get() == 0.85
    }

    def "should return reverse exchange rate when direct rate not available"() {
        given:
        def reversePair = new CurrencyPair("EUR", "USD")
        ExchangeRates.setExchangeRate(reversePair, 1.18)

        when:
        def result = ExchangeRates.getExchangeRate(new CurrencyPair("USD", "EUR"))

        then:
        result.isPresent()
        Math.abs(result.get() - 1 / 1.18) <= 0.00001
    }

    def "should calculate cross rate when direct and reverse rates not available"() {
        given:
        def usdGbp = new CurrencyPair("USD", "GBP")
        def eurGbp = new CurrencyPair("EUR", "GBP")
        ExchangeRates.setExchangeRate(usdGbp, 0.73)
        ExchangeRates.setExchangeRate(eurGbp, 0.86)

        when:
        def result = ExchangeRates.getExchangeRate(new CurrencyPair("USD", "EUR"))

        then:
        result.isPresent()
        Math.abs(result.get() - 0.73 / 0.86) <= 0.00001
    }

    def "should calculate cross rate when direct and reverse rates not available from quote currency"() {
        given:
        def gbpUsd = new CurrencyPair("GBP" , "USD")
        def gbpEur = new CurrencyPair("GBP" , "EUR")
        ExchangeRates.setExchangeRate(gbpUsd, 0.73)
        ExchangeRates.setExchangeRate(gbpEur, 0.86)

        when:
        def result = ExchangeRates.getExchangeRate(new CurrencyPair("USD", "EUR"))

        then:
        result.isPresent()
        Math.abs(result.get() - 0.86 / 0.73) <= 0.00001
    }

    def "should return empty when no exchange rate available"() {
        given:
        def pair = new CurrencyPair("USD", "JPY")

        when:
        def result = ExchangeRates.getExchangeRate(pair)

        then:
        !result.isPresent()
    }
}