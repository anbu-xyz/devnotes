package uk.anbu.devnotes.types;

import java.math.BigDecimal;

public record Cash(BigDecimal amount, String currency) {

    public Cash {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
    }

    public static Cash from(String currency, BigDecimal amount) {
        return new Cash(amount, currency);
    }

    public static Cash from(String currencyAmount) {
        var split = currencyAmount.split(" ", 2);
        var amount = new BigDecimal(split[1]);
        var currency = split[0];
        return new Cash(amount, currency);
    }

    public String toString() {
        return currency + " " + amount.toString();
    }

    public Cash times(Double multiplier) {
        return new Cash(amount.multiply(new BigDecimal(multiplier)), currency);
    }

    public Cash plus(Cash other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currencies must be the same");
        }
        return new Cash(amount.add(other.amount), currency);
    }

    public Cash minus(Cash other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currencies must be the same");
        }
        return new Cash(amount.subtract(other.amount), currency);
    }

}
