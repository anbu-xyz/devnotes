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
import org.springframework.web.multipart.MultipartFile;
import uk.anbu.devnotes.cash.CurrencyCodes;
import uk.anbu.devnotes.service.ExchangeRateService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

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

    @PostMapping("/tools/exchange-rates/upload")
    public ResponseEntity<String> uploadRates(@RequestParam MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return render(null, null, new UploadResult(0, List.of("No file selected or file is empty.")));
        }

        var errors = new ArrayList<String>();
        var validRates = new LinkedHashMap<String, Double>();
        int lineNumber = 0;
        boolean headerSkipped = false;

        try (var reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                var trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                var columns = trimmed.split(",", -1);
                if (columns.length < 2) {
                    errors.add("Line " + lineNumber + ": expected 2 columns but found " + columns.length);
                    continue;
                }

                var rawPair = columns[0].trim();
                var rawRate = columns[1].trim();

                var normalPair = rawPair.toUpperCase();

                // Skip a header row only when the pair column clearly isn't data: it
                // neither matches a valid BASE/QUOTE pair nor is it purely alphabetic
                // (purely-alpha values like "GBPUSD" are treated as malformed data rows).
                if (!headerSkipped
                        && !normalPair.matches("[A-Z]{3}/[A-Z]{3}")
                        && !normalPair.matches("[A-Z]+")) {
                    headerSkipped = true;
                    continue;
                }
                headerSkipped = true;

                if (!normalPair.matches("[A-Z]{3}/[A-Z]{3}")) {
                    errors.add("Line " + lineNumber + ": invalid pair format '" + rawPair + "' - must be BASE/QUOTE");
                    continue;
                }

                var parts = normalPair.split("/", 2);
                if (!CurrencyCodes.isValidCurrencyCode(parts[0])) {
                    errors.add("Line " + lineNumber + ": unknown base currency code '" + parts[0] + "'");
                    continue;
                }
                if (!CurrencyCodes.isValidCurrencyCode(parts[1])) {
                    errors.add("Line " + lineNumber + ": unknown quote currency code '" + parts[1] + "'");
                    continue;
                }

                double parsedRate;
                try {
                    parsedRate = Double.parseDouble(rawRate);
                } catch (NumberFormatException e) {
                    errors.add("Line " + lineNumber + ": rate '" + rawRate + "' is not a valid number");
                    continue;
                }
                if (parsedRate <= 0) {
                    errors.add("Line " + lineNumber + ": rate must be greater than zero (got " + rawRate + ")");
                    continue;
                }

                validRates.put(normalPair, parsedRate);
            }
        } catch (Exception e) {
            log.error("Failed to read uploaded CSV: {}", e.getMessage(), e);
            return render(null, null, new UploadResult(0, List.of("Failed to read file: " + e.getMessage())));
        }

        if (!validRates.isEmpty()) {
            exchangeRateService.bulkAddRates(validRates);
        }

        return render(null, null, new UploadResult(validRates.size(), errors));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    public record UploadResult(int imported, List<String> errors) {}

    private ResponseEntity<String> render(String errorMessage) {
        return render(errorMessage, null, null);
    }

    private ResponseEntity<String> render(String errorMessage, String successMessage, UploadResult uploadResult) {
        var model = new HashMap<String, Object>();
        model.put("rates", exchangeRateService.getAllRates());
        model.put("errorMessage", errorMessage);
        model.put("successMessage", successMessage);
        model.put("uploadResult", uploadResult);
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

