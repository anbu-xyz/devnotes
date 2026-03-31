package uk.anbu.devnotes.controller;

import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devnotes.cash.CurrencyCodes;
import uk.anbu.devnotes.service.ExchangeRateService;

import java.util.HashMap;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;
    private final TemplateEngine templateEngine;

    @GetMapping("/tools/exchange-rates")
    public ResponseEntity<String> exchangeRatesPage() {
        return render(null);
    }

    @PostMapping("/tools/exchange-rates")
    public ResponseEntity<String> addRate(
            @RequestParam String pair,
            @RequestParam String rate) {

        String trimmedPair = pair == null ? "" : pair.trim().toUpperCase();
        String trimmedRate = rate == null ? "" : rate.trim();

        // Validate pair format
        if (!trimmedPair.matches("[A-Z]{3}/[A-Z]{3}")) {
            return render("Pair must be in BASE/QUOTE format, e.g. GBP/USD");
        }

        // Validate both currency codes
        String[] parts = trimmedPair.split("/", 2);
        if (!CurrencyCodes.isValidCurrencyCode(parts[0])) {
            return render("Unknown currency code: " + parts[0]);
        }
        if (!CurrencyCodes.isValidCurrencyCode(parts[1])) {
            return render("Unknown currency code: " + parts[1]);
        }

        // Validate rate
        double parsedRate;
        try {
            parsedRate = Double.parseDouble(trimmedRate);
        } catch (NumberFormatException e) {
            return render("Rate must be a positive number");
        }
        if (parsedRate <= 0) {
            return render("Rate must be greater than zero");
        }

        try {
            exchangeRateService.addRate(trimmedPair, parsedRate);
        } catch (IllegalArgumentException e) {
            return render(e.getMessage());
        }

        return redirect();
    }

    @PostMapping("/tools/exchange-rates/delete")
    public ResponseEntity<String> deleteRate(@RequestParam String pair) {
        exchangeRateService.deleteRate(pair == null ? "" : pair.trim());
        return redirect();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private ResponseEntity<String> render(String errorMessage) {
        var model = new HashMap<String, Object>();
        model.put("rates", exchangeRateService.getAllRates());
        model.put("errorMessage", errorMessage);
        TemplateOutput output = new StringOutput();
        templateEngine.render("tools/exchange-rates.jte", model, output);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8")
                .body(output.toString());
    }

    private ResponseEntity<String> redirect() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "/tools/exchange-rates")
                .build();
    }
}

