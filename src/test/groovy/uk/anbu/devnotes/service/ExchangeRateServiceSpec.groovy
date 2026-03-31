package uk.anbu.devnotes.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import spock.lang.Specification
import spock.lang.TempDir
import uk.anbu.devnotes.cash.ExchangeRates
import uk.anbu.devnotes.types.CurrencyPair

import java.nio.file.Files
import java.nio.file.Path

class ExchangeRateServiceSpec extends Specification {

    @TempDir
    Path tempDir

    ConfigService configService
    ExchangeRateService service

    def setup() {
        ExchangeRates.clear()
        configService = Mock(ConfigService)
        configService.getDocsDirectory() >> tempDir.toString()
        service = new ExchangeRateService(configService)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Path ratesFile() {
        tempDir.resolve("config/exchange-rates.yaml")
    }

    private void writeRatesYaml(Map<String, Double> rates) {
        def dir = tempDir.resolve("config")
        Files.createDirectories(dir)
        def mapper = new ObjectMapper(new YAMLFactory())
        mapper.writeValue(ratesFile().toFile(), rates)
    }

    // =========================================================================
    // init
    // =========================================================================

    def "loads rates from yaml file on init and populates ExchangeRates"() {
        given: "a pre-existing exchange-rates.yaml"
        writeRatesYaml(["GBP/USD": 1.27, "EUR/USD": 1.085])

        when:
        service.init()

        then:
        service.getAllRates() == ["GBP/USD": 1.27, "EUR/USD": 1.085]
        ExchangeRates.getExchangeRate(new CurrencyPair("GBP", "USD")).get() == 1.27
        ExchangeRates.getExchangeRate(new CurrencyPair("EUR", "USD")).get() == 1.085
    }

    def "init with no yaml file starts with empty rates"() {
        when:
        service.init()

        then:
        service.getAllRates().isEmpty()
    }

    def "init skips entries with invalid currency codes and logs a warning"() {
        given:
        writeRatesYaml(["GBP/USD": 1.27, "XXX/YYY": 0.5])

        when:
        service.init()

        then: "only the valid pair is loaded"
        service.getAllRates().size() == 1
        service.getAllRates().containsKey("GBP/USD")
    }

    // =========================================================================
    // addRate
    // =========================================================================

    def "addRate persists new entry to yaml and updates ExchangeRates"() {
        given:
        service.init()

        when:
        service.addRate("GBP/USD", 1.27)

        then:
        service.getAllRates() == ["GBP/USD": 1.27]
        ExchangeRates.getExchangeRate(new CurrencyPair("GBP", "USD")).get() == 1.27

        and: "yaml file is written"
        def saved = new ObjectMapper(new YAMLFactory()).readValue(ratesFile().toFile(), Map)
        saved["GBP/USD"] == 1.27
    }

    def "addRate overwrites an existing pair"() {
        given:
        service.init()
        service.addRate("GBP/USD", 1.25)

        when:
        service.addRate("GBP/USD", 1.30)

        then:
        service.getAllRates()["GBP/USD"] == 1.30
    }

    def "addRate rejects invalid base currency code"() {
        given:
        service.init()

        when:
        service.addRate("XYZ/USD", 1.0)

        then:
        thrown(IllegalArgumentException)
        service.getAllRates().isEmpty()
    }

    def "addRate rejects invalid quote currency code"() {
        given:
        service.init()

        when:
        service.addRate("GBP/XYZ", 1.0)

        then:
        thrown(IllegalArgumentException)
    }

    def "addRate rejects pair without slash"() {
        given:
        service.init()

        when:
        service.addRate("GBPUSD", 1.27)

        then:
        thrown(IllegalArgumentException)
    }

    // =========================================================================
    // deleteRate
    // =========================================================================

    def "deleteRate removes entry from yaml"() {
        given:
        service.init()
        service.addRate("GBP/USD", 1.27)
        service.addRate("EUR/USD", 1.085)

        when:
        service.deleteRate("GBP/USD")

        then:
        !service.getAllRates().containsKey("GBP/USD")
        service.getAllRates().containsKey("EUR/USD")

        and: "yaml file does not contain the deleted pair"
        def saved = new ObjectMapper(new YAMLFactory()).readValue(ratesFile().toFile(), Map)
        !saved.containsKey("GBP/USD")
        saved.containsKey("EUR/USD")
    }

    def "deleteRate on non-existent key is a no-op"() {
        given:
        service.init()
        service.addRate("EUR/USD", 1.085)

        when:
        service.deleteRate("GBP/USD")

        then:
        service.getAllRates().size() == 1
        noExceptionThrown()
    }

    // =========================================================================
    // getAllRates
    // =========================================================================

    def "getAllRates returns rates in insertion order"() {
        given:
        service.init()
        service.addRate("GBP/USD", 1.27)
        service.addRate("EUR/USD", 1.085)
        service.addRate("JPY/USD", 0.0068)

        when:
        def keys = service.getAllRates().keySet().toList()

        then:
        keys == ["GBP/USD", "EUR/USD", "JPY/USD"]
    }

    def "getAllRates returns unmodifiable view"() {
        given:
        service.init()

        when:
        service.getAllRates().put("GBP/USD", 1.0)

        then:
        thrown(UnsupportedOperationException)
    }

    // =========================================================================
    // getVersion
    // =========================================================================

    def "getVersion returns 0 when no yaml file exists"() {
        when:
        service.init()

        then:
        service.getVersion() == 0L
    }

    def "getVersion changes after save"() {
        given:
        service.init()
        long versionBefore = service.getVersion()

        when:
        service.addRate("GBP/USD", 1.27)
        long versionAfter = service.getVersion()

        then:
        versionAfter > 0L
        versionAfter >= versionBefore
    }
}

