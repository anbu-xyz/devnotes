package uk.anbu.devnotes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.anbu.devnotes.cash.CurrencyCodes;
import uk.anbu.devnotes.cash.ExchangeRates;
import uk.anbu.devnotes.types.CurrencyPair;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateService {

    private final ConfigService configService;

    /** Explicit pairs only (no derived reverses). */
    private final LinkedHashMap<String, Double> rates = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        File yamlFile = ratesFile();
        if (!yamlFile.exists()) {
            log.info("No exchange-rates.yaml found at {}, starting with empty rates", yamlFile);
            return;
        }
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            @SuppressWarnings("unchecked")
            Map<String, Double> loaded = mapper.readValue(yamlFile, Map.class);
            if (loaded != null) {
                loaded.forEach((pairStr, rate) -> {
                    try {
                        CurrencyPair pair = parsePair(pairStr);
                        rates.put(pairStr, rate);
                        ExchangeRates.setExchangeRate(pair, rate);
                    } catch (Exception e) {
                        log.warn("Skipping invalid exchange rate entry '{}': {}", pairStr, e.getMessage());
                    }
                });
            }
            log.info("Loaded {} exchange rate(s) from {}", rates.size(), yamlFile);
        } catch (IOException e) {
            log.error("Failed to load exchange-rates.yaml: {}", e.getMessage(), e);
        }
    }

    /**
     * Add or update a rate. Persists to disk.
     *
     * @param pairString e.g. {@code GBP/USD}
     * @param rate       positive exchange rate
     */
    public void addRate(String pairString, double rate) {
        CurrencyPair pair = parsePair(pairString);  // validates codes
        ExchangeRates.setExchangeRate(pair, rate);
        rates.put(pairString, rate);
        save();
    }

    /**
     * Add or update multiple rates in a single operation. Persists to disk once
     * after all rates have been applied.
     *
     * @param ratesToAdd map of pair strings (e.g. {@code GBP/USD}) to positive rates
     */
    public void bulkAddRates(Map<String, Double> ratesToAdd) {
        ratesToAdd.forEach((pairString, rate) -> {
            CurrencyPair pair = parsePair(pairString);
            ExchangeRates.setExchangeRate(pair, rate);
            rates.put(pairString, rate);
        });
        save();
    }

    /**
     * Remove an explicitly stored rate. Derived reverse entries remain in
     * {@link ExchangeRates} until the next restart.
     *
     * @param pairString e.g. {@code GBP/USD}
     */
    public void deleteRate(String pairString) {
        rates.remove(pairString);
        save();
    }

    /** Returns an unmodifiable view of explicit rates in insertion order. */
    public Map<String, Double> getAllRates() {
        return Collections.unmodifiableMap(rates);
    }

    /**
     * Returns the last-modified epoch millis of {@code exchange-rates.yaml},
     * or {@code 0L} when the file is absent.
     */
    public long getVersion() {
        File f = ratesFile();
        return f.exists() ? f.lastModified() : 0L;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void save() {
        File yamlFile = ratesFile();
        yamlFile.getParentFile().mkdirs();
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            mapper.writeValue(yamlFile, rates);
            log.debug("Saved {} exchange rate(s) to {}", rates.size(), yamlFile);
        } catch (IOException e) {
            log.error("Failed to save exchange-rates.yaml: {}", e.getMessage(), e);
        }
    }

    private File ratesFile() {
        return new File(configService.getDocsDirectory(), "config/exchange-rates.yaml");
    }

    /**
     * Parse a {@code BASE/QUOTE} string into a {@link CurrencyPair}, validating
     * both codes via {@link CurrencyCodes}.
     */
    private static CurrencyPair parsePair(String pairString) {
        if (pairString == null || !pairString.contains("/")) {
            throw new IllegalArgumentException("Currency pair must be in BASE/QUOTE format: " + pairString);
        }
        String[] parts = pairString.split("/", 2);
        String base = parts[0].trim().toUpperCase();
        String quote = parts[1].trim().toUpperCase();
        if (!CurrencyCodes.isValidCurrencyCode(base)) {
            throw new IllegalArgumentException("Invalid base currency code: " + base);
        }
        if (!CurrencyCodes.isValidCurrencyCode(quote)) {
            throw new IllegalArgumentException("Invalid quote currency code: " + quote);
        }
        return new CurrencyPair(base, quote);
    }
}

