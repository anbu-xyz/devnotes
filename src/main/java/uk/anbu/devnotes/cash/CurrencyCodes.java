package uk.anbu.devnotes.cash;

import lombok.extern.slf4j.Slf4j;
import uk.anbu.devnotes.types.CurrencyCode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class CurrencyCodes {
    // Currency codes data source: https://datahub.io/core/currency-codes#codes-all

    private static final Map<String, CurrencyCode> currencyCodesByAlpha = new HashMap<>();
    private static final Map<String, CurrencyCode> currencyCodesByNumeric = new HashMap<>();

    static {
        try {
            loadCurrencyCodes();
        } catch (Exception e) {
            log.error("Error loading currency codes", e);
        }
    }

    static public void loadCurrencyCodes() {
        try (InputStream is = CurrencyCodes.class.getResourceAsStream("/currency-codes.csv");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

            // Skip header
            reader.readLine();

            String line;
            while ((line = reader.readLine()) != null) {
                var code = processLine(line);
                if (code.isPresent() && !code.get().alphabeticCode().isEmpty()) {
                    currencyCodesByAlpha.put(code.get().alphabeticCode(), code.get());
                    if (!code.get().numericCode().isEmpty()) {
                        currencyCodesByNumeric.put(code.get().numericCode(), code.get());
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load currency codes", e);
        }
    }

    static Optional<CurrencyCode> processLine(String line) {
        if (line == null || line.isEmpty()) {
            log.warn("Empty currency code line");
            return Optional.empty();
        }

        var parts = parseCsvLine(line);
        if (parts.size() < 6) {
            log.warn("Invalid currency code line: {}", line);
            return Optional.empty();
        }

        // Skip if no alphabetic code or currency name
        if (parts.get(2).isEmpty() || parts.get(1).isEmpty()) {
            log.warn("Invalid currency code line. No alphabetic code or currency name: {}", line);
            return Optional.empty();
        }

        // Parse minor unit, default to 0 if empty or invalid
        int minorUnit = 0;
        try {
            if (!parts.get(4).isEmpty()) {
                minorUnit = Integer.parseInt(parts.get(4));
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid currency code line. Invalid minor unit: {}", line);
        }

        return Optional.of(
                CurrencyCode.builder()
                        .entity(parts.get(0))
                        .currency(parts.get(1))
                        .alphabeticCode(parts.get(2))
                        .numericCode(parts.get(3))
                        .minorUnit(minorUnit)
                        .withdrawalDate(parts.get(5))
                        .build()
        );
    }

    private static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder currentValue = new StringBuilder();
        boolean inQuotes = false;

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                values.add(currentValue.toString());
                currentValue = new StringBuilder();
            } else {
                currentValue.append(c);
            }
        }
        values.add(currentValue.toString());

        return Collections.unmodifiableList(values);
    }

    public static CurrencyCode getByAlphabeticCode(String code) {
        return currencyCodesByAlpha.get(code);
    }

    public static CurrencyCode getByNumericCode(String code) {
        return currencyCodesByNumeric.get(code);
    }

    public static boolean isValidCurrencyCode(String code) {
        return getByAlphabeticCode(code) != null;
    }

}
