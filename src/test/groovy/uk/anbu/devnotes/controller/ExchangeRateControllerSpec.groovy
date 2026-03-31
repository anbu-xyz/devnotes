package uk.anbu.devnotes.controller

import gg.jte.ContentType
import gg.jte.TemplateEngine
import gg.jte.resolve.DirectoryCodeResolver
import org.jsoup.Jsoup
import org.springframework.http.HttpStatus
import spock.lang.Specification
import spock.lang.TempDir
import uk.anbu.devnotes.cash.ExchangeRates
import uk.anbu.devnotes.service.ConfigService
import uk.anbu.devnotes.service.ExchangeRateService

import java.nio.file.Path
import java.nio.file.Paths

class ExchangeRateControllerSpec extends Specification {

    @TempDir
    Path tempDir

    ExchangeRateController controller
    ExchangeRateService exchangeRateService

    def setup() {
        ExchangeRates.clear()

        ConfigService configService = Mock(ConfigService)
        configService.getDocsDirectory() >> tempDir.toString()

        exchangeRateService = new ExchangeRateService(configService)
        exchangeRateService.init()

        def codeResolver = new DirectoryCodeResolver(Paths.get("src/main/jte"))
        def te = TemplateEngine.create(codeResolver, Paths.get("src/main/jte"), ContentType.Html)

        controller = new ExchangeRateController(exchangeRateService, te)
    }

    // =========================================================================
    // GET /tools/exchange-rates
    // =========================================================================

    def "GET /tools/exchange-rates returns 200 with Exchange Rate Manager heading"() {
        when:
        def response = controller.exchangeRatesPage()

        then:
        response.statusCode.value() == 200
        response.body.contains("Exchange Rate Manager")
    }

    def "GET /tools/exchange-rates shows no-rates message when map is empty"() {
        when:
        def response = controller.exchangeRatesPage()

        then:
        def doc = Jsoup.parse(response.body)
        doc.body().text().contains("No rates configured yet")
    }

    def "GET /tools/exchange-rates shows rates table when rates are present"() {
        given:
        exchangeRateService.addRate("GBP/USD", 1.27)
        exchangeRateService.addRate("EUR/USD", 1.085)

        when:
        def response = controller.exchangeRatesPage()

        then:
        def doc = Jsoup.parse(response.body)
        def rows = doc.select("table tbody tr")
        rows.size() == 2
        rows[0].select("td")[0].text() == "GBP/USD"
        rows[1].select("td")[0].text() == "EUR/USD"
    }

    def "GET /tools/exchange-rates includes back link to /tools"() {
        when:
        def response = controller.exchangeRatesPage()

        then:
        def doc = Jsoup.parse(response.body)
        doc.select("a[href='/tools']").size() > 0
    }

    // =========================================================================
    // POST /tools/exchange-rates — add rate
    // =========================================================================

    def "POST with valid pair and rate redirects to GET"() {
        when:
        def response = controller.addRate("GBP/USD", "1.27")

        then:
        response.statusCode == HttpStatus.FOUND
        response.headers.getLocation().toString() == "/tools/exchange-rates"
    }

    def "POST with valid pair and rate saves rate"() {
        when:
        controller.addRate("GBP/USD", "1.27")

        then:
        exchangeRateService.getAllRates()["GBP/USD"] == 1.27
    }

    def "POST with invalid pair format shows error page"() {
        when:
        def response = controller.addRate("GBPUSD", "1.27")

        then:
        response.statusCode == HttpStatus.OK
        def doc = Jsoup.parse(response.body)
        doc.select(".encryption-error, [class*='error']").size() > 0
    }

    def "POST with lowercase pair is accepted and normalised to uppercase"() {
        when:
        def response = controller.addRate("gbp/usd", "1.27")

        then:
        response.statusCode == HttpStatus.FOUND
        exchangeRateService.getAllRates().containsKey("GBP/USD")
    }

    def "POST with unknown base currency code shows error page"() {
        when:
        def response = controller.addRate("XYZ/USD", "1.0")

        then:
        response.statusCode == HttpStatus.OK
        response.body.contains("XYZ")
    }

    def "POST with unknown quote currency code shows error page"() {
        when:
        def response = controller.addRate("GBP/XYZ", "1.0")

        then:
        response.statusCode == HttpStatus.OK
        response.body.contains("XYZ")
    }

    def "POST with non-numeric rate shows error page"() {
        when:
        def response = controller.addRate("GBP/USD", "notanumber")

        then:
        response.statusCode == HttpStatus.OK
        response.body.contains("positive number")
    }

    def "POST with zero rate shows error page"() {
        when:
        def response = controller.addRate("GBP/USD", "0")

        then:
        response.statusCode == HttpStatus.OK
        response.body.contains("greater than zero")
    }

    def "POST with negative rate shows error page"() {
        when:
        def response = controller.addRate("GBP/USD", "-1.5")

        then:
        response.statusCode == HttpStatus.OK
        response.body.contains("greater than zero")
    }

    // =========================================================================
    // POST /tools/exchange-rates/delete
    // =========================================================================

    def "POST /delete removes rate and redirects to GET"() {
        given:
        exchangeRateService.addRate("GBP/USD", 1.27)

        when:
        def response = controller.deleteRate("GBP/USD")

        then:
        response.statusCode == HttpStatus.FOUND
        response.headers.getLocation().toString() == "/tools/exchange-rates"
        !exchangeRateService.getAllRates().containsKey("GBP/USD")
    }

    def "POST /delete on non-existent pair still redirects"() {
        when:
        def response = controller.deleteRate("EUR/USD")

        then:
        response.statusCode == HttpStatus.FOUND
        noExceptionThrown()
    }
}

