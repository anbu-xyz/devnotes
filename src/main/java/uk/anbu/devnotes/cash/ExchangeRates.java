package uk.anbu.devnotes.cash;

import org.springframework.stereotype.Component;
import uk.anbu.devnotes.types.CurrencyPair;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class ExchangeRates {
    private static final Map<CurrencyPair, Double> exchangeRates = new HashMap<>();

    static void clear() {
        exchangeRates.clear();
    }

    public static void setExchangeRate(CurrencyPair currencyPair, double rate) {
        exchangeRates.put(currencyPair, rate);
        // find reverse pair
        var reversePair = new CurrencyPair(currencyPair.quote(), currencyPair.base());
        if (!exchangeRates.containsKey(reversePair)) {
            exchangeRates.put(reversePair, 1 / rate);
        }
    }

    public static Optional<Double> getExchangeRate(CurrencyPair currencyPair) {
        var rate =  Optional.ofNullable(exchangeRates.get(currencyPair));
        if (rate.isPresent()) {
            return rate;
        }

        var reversePair = new CurrencyPair(currencyPair.quote(), currencyPair.base());
        rate = Optional.ofNullable(exchangeRates.get(reversePair));
        if (rate.isPresent()) {
            return Optional.of(1 / rate.get());
        }

        // find pairs having save a different quote for the given base and quote
        var pairs = exchangeRates.keySet().stream()
                .filter(pair -> pair.base().equals(currencyPair.quote()) || pair.base().equals(currencyPair.base()))
                .sorted(Comparator.comparing(CurrencyPair::quote))
                .toList();

        // go through the pairs and identify those with same quote and different base
        for (int i = 0; i < pairs.size(); i++) {
            var firstPair = pairs.get(i);
            var nextPair = pairs.get(i + 1);
            if (firstPair.quote().equals(nextPair.quote())) {
                // use pair and nextPair to calculate exchange rate
                var basePair = new CurrencyPair(currencyPair.base(), firstPair.quote());
                var quotePair = new CurrencyPair(currencyPair.quote(), firstPair.quote());
                var calculatedRate = exchangeRates.get(basePair) / exchangeRates.get(quotePair);
                return Optional.of(calculatedRate);
            }
        }

        return Optional.empty();
    }

}
