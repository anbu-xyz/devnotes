package uk.anbu.devnotes.types;

import lombok.Builder;

@Builder
public record CurrencyCode(String entity, String currency, String alphabeticCode,
                           String numericCode, int minorUnit, String withdrawalDate) {
}
