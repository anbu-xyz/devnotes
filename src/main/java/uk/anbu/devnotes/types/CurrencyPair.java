package uk.anbu.devnotes.types;

import uk.anbu.devnotes.cash.CurrencyCodes;

public record CurrencyPair(String base, String quote) {
    public CurrencyPair {
        if (!CurrencyCodes.isValidCurrencyCode(base)) {
            throw new IllegalArgumentException("Invalid base currency code: " + base);
        }
        if (!CurrencyCodes.isValidCurrencyCode(quote)) {
            throw new IllegalArgumentException("Invalid quote currency code: " + quote);
        }
    }

    public String toString() {
        return base + "/" + quote;
    }
}
