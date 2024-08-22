package uk.anbu.devnotes.module;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Test
    void testSortDataWithIntegers() {
        var dataNode = new ArrayList<Map<String, Object>>();
        dataNode.add(createRow("id", 3, "name", "Charlie"));
        dataNode.add(createRow("id", 1, "name", "Alice"));
        dataNode.add(createRow("id", 2, "name", "Bob"));

        List<Map<String, Object>> result = SqlExecutor.sortData(dataNode, "id", "java.lang.Integer", "asc");

        assertEquals(1, result.get(0).get("id"));
        assertEquals(2, result.get(1).get("id"));
        assertEquals(3, result.get(2).get("id"));
    }

    @Test
    void testSortDataWithStrings() {
        var dataNode = new ArrayList<Map<String, Object>>();
        dataNode.add(createRow("id", 1, "name", "Charlie"));
        dataNode.add(createRow("id", 2, "name", "Alice"));
        dataNode.add(createRow("id", 3, "name", "Bob"));

        List<Map<String, Object>> result = SqlExecutor.sortData(dataNode, "name", "java.lang.String", "desc");

        assertEquals("Charlie", result.get(0).get("name"));
        assertEquals("Bob", result.get(1).get("name"));
        assertEquals("Alice", result.get(2).get("name"));
    }

    @Test
    void testSortDataWithDoubles() {
        var dataNode = new ArrayList<Map<String, Object>>();
        dataNode.add(createRow("id", 1, "score", 3.5));
        dataNode.add(createRow("id", 2, "score", 1.2));
        dataNode.add(createRow("id", 3, "score", 2.8));

        List<Map<String, Object>> result = SqlExecutor.sortData(dataNode, "score", "java.lang.Double", "asc");

        assertEquals(1.2, result.get(0).get("score"));
        assertEquals(2.8, result.get(1).get("score"));
        assertEquals(3.5, result.get(2).get("score"));
    }

    @Test
    void testSortDataWithNullValues() {
        var dataNode = new ArrayList<Map<String, Object>>();
        dataNode.add(createRow("id", 1, "name", "Alice"));
        dataNode.add(createRow("id", 2, "name", null));
        dataNode.add(createRow("id", 3, "name", "Bob"));

        List<Map<String, Object>> result = SqlExecutor.sortData(dataNode, "name", "java.lang.String", "asc");

        assertEquals(null, result.get(0).get("name"));
        assertEquals("Alice", result.get(1).get("name"));
        assertEquals("Bob", result.get(2).get("name"));
    }

    @Test
    void testSortDataWithTimestamps() {
        var dataNode = new ArrayList<Map<String, Object>>();
        dataNode.add(createRow("id", 1, "timestamp", "2023-05-01 10:00:00"));
        dataNode.add(createRow("id", 2, "timestamp", "2023-05-01 09:00:00"));
        dataNode.add(createRow("id", 3, "timestamp", "2023-05-01 11:00:00"));

        List<Map<String, Object>> result = SqlExecutor.sortData(dataNode, "timestamp", "java.sql.Timestamp", "asc");

        assertEquals("2023-05-01 09:00:00", result.get(0).get("timestamp"));
        assertEquals("2023-05-01 10:00:00", result.get(1).get("timestamp"));
        assertEquals("2023-05-01 11:00:00", result.get(2).get("timestamp"));
    }

    private Map<String, Object> createRow(Object... keyValues) {
        Map<String, Object> result = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            String key = keyValues[i].toString();
            Object value = keyValues[i + 1];
            if (value == null) {
                result.put(key, null);
            } else if (value instanceof Integer) {
                result.put(key, value);
            } else if (value instanceof String) {
                result.put(key, value);
            } else if (value instanceof Double) {
                result.put(key, value);
            }
        }
        return result;
    }

}
