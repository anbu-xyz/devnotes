package uk.anbu.devnotes.module;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlExecutorTest {

    @ParameterizedTest
    @MethodSource("provideNumbersForFormatting")
    void testHumanReadableNumber(Number input, String expected) {
        assertEquals(expected, SqlExecutor.humanReadableNumber(input));
    }

    private static Stream<Arguments> provideNumbersForFormatting() {
        return Stream.of(
                Arguments.of(null, "(null)"),
                Arguments.of(0, "0"),
                Arguments.of(1000, "1,000"),
                Arguments.of(1000000, "1,000,000"),
                Arguments.of(1234567890, "1,234,567,890"),
                Arguments.of(1234567890.12345, "1,234,567,890.12345"),
                Arguments.of(0.1234567890, "0.123456789"),
                Arguments.of(new BigDecimal("1E20"), "100,000,000,000,000,000,000"),
                Arguments.of(new BigDecimal("1E-10"), "0.0000000001"),
                Arguments.of(Double.MAX_VALUE, "179,769,313,486,231,570,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000,000"),
                Arguments.of(Double.MIN_VALUE, "0.0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000049"),
                Arguments.of(new BigDecimal("0.00001"), "0.00001"),
                Arguments.of(new BigDecimal("-1234567890.12345"), "-1,234,567,890.12345"),
                Arguments.of(new BigDecimal("9999999999999999999999"), "9,999,999,999,999,999,999,999")
        );
    }
}
