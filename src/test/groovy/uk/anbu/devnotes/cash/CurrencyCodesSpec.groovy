package uk.anbu.devnotes.cash

import spock.lang.Specification
import spock.lang.Unroll

class CurrencyCodesSpec extends Specification {

    CurrencyCodes currencyCodes

    def setup() {
        currencyCodes = new CurrencyCodes()
    }

    @Unroll
    def "processLine should correctly parse valid currency line: #description"() {
        when:
        def result = currencyCodes.processLine(input)

        then:
        result.isPresent()
        with(result.get()) {
            entity == expectedEntity
            currency == expectedCurrency
            alphabeticCode == expectedAlphaCode
            numericCode == expectedNumericCode
            minorUnit == expectedMinorUnit
            withdrawalDate == expectedWithdrawalDate
        }

        where:
        description         | input                                                       | expectedEntity                     | expectedCurrency | expectedAlphaCode | expectedNumericCode | expectedMinorUnit | expectedWithdrawalDate
        "standard currency" | "AFGHANISTAN,Afghani,AFN,971,2,"                            | "AFGHANISTAN"                      | "Afghani"        | "AFN"             | "971"               | 2                 | ""
        "with withdrawal"   | "ALBANIA,Old Lek,ALK,008,,1989-12"                          | "ALBANIA"                          | "Old Lek"        | "ALK"             | "008"               | 0                 | "1989-12"
        "quoted values"     | "\"BOLIVIA (PLURINATIONAL STATE OF)\",Boliviano,BOB,068,2," | "BOLIVIA (PLURINATIONAL STATE OF)" | "Boliviano"      | "BOB"             | "068"               | 2                 | ""
        "zero minor units"  | "JAPAN,Yen,JPY,392,0,"                                      | "JAPAN"                            | "Yen"            | "JPY"             | "392"               | 0                 | ""
    }

    @Unroll
    def "processLine should handle invalid inputs: #description"() {
        when:
        def result = currencyCodes.processLine(input)

        then:
        result.isEmpty() == expectedEmpty

        where:
        description          | input                            | expectedEmpty
        "empty line"         | ""                               | true
        "missing alpha code" | "ANTARCTICA,No currency,,,"      | true
        "missing currency"   | "ANTARCTICA,,XXX,,,,"            | true
        "invalid minor unit" | "TEST,Currency,CUR,123,invalid," | false
        "null input"         | null                             | true
    }

    def "processLine should handle lines with invalid number of columns"() {
        when:
        def result = currencyCodes.processLine("TOO,FEW,COLUMNS")

        then:
        result.isEmpty()
    }

    def "processLine should default minor unit to 0 when empty"() {
        when:
        def result = currencyCodes.processLine("ENTITY,Currency,CUR,123,,")

        then:
        result.isPresent()
        result.get().minorUnit() == 0
    }

    def "processLine should handle complex quoted strings"() {
        given:
        def input = "\"CONGO (THE DEMOCRATIC REPUBLIC OF THE)\",\"Congolese Franc\",CDF,976,2,"

        when:
        def result = currencyCodes.processLine(input)

        then:
        result.isPresent()
        with(result.get()) {
            entity == "CONGO (THE DEMOCRATIC REPUBLIC OF THE)"
            currency == "Congolese Franc"
            alphabeticCode == "CDF"
            numericCode == "976"
            minorUnit == 2
            withdrawalDate == ""
        }
    }

    def "processLine should handle embedded commas in quoted strings"() {
        given:
        def input = "\"ENTITY, WITH, COMMAS\",\"Currency, with, commas\",CUR,123,2,"

        when:
        def result = currencyCodes.processLine(input)

        then:
        result.isPresent()
        with(result.get()) {
            entity == "ENTITY, WITH, COMMAS"
            currency == "Currency, with, commas"
            alphabeticCode == "CUR"
            numericCode == "123"
            minorUnit == 2
            withdrawalDate == ""
        }
    }
}
