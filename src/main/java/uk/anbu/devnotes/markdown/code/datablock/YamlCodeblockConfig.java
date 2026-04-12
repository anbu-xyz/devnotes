package uk.anbu.devnotes.markdown.code.datablock;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Data
public class YamlCodeblockConfig {
    private String source;
    private String query;
    private String header;

    @JsonProperty("hide-row-count")
    private boolean hideRowCount = false;
    @JsonProperty("columns-to-exclude")
    private List<String> columnsToExclude = List.of();
    @JsonProperty("combine-single-column")
    private boolean combineSingleColumn = true;
    @JsonProperty("hide-row-count-when-less-than")
    private int hideRowCountWhenLessThan = 10;
    @JsonProperty("transpose")
    private boolean transpose = false;

    private SqlOptions options;
    private Map<String, SqlParameter> parameters = Map.of();

    @JsonProperty("column-formats")
    private Map<String, ColumnFormatConfig> columnFormats = Map.of();


    @Data
    public static class SqlOptions {
        @JsonProperty("row-limit")
        private int rowLimit = 100;
        private List<String> columns;
    }

    @Data
    public static class SqlParameter {
        private Object value;
        // optional type: integer, long, double, boolean, string, date, timestamp, blob, etc.
        private String type;

        @JsonCreator
        public SqlParameter(Object raw) {
            if (raw == null) return;
            if (raw instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) raw;
                this.value = m.get("value");
                Object t = m.get("type");
                this.type = t == null ? null : t.toString();
            } else {
                // scalar shorthand
                this.value = raw;
            }
        }

        // default constructor for Jackson when provided an object with properties
        public SqlParameter() {}
    }

    @Data
    public static class ColumnFormatConfig {
        /**
         * A {@link java.text.DecimalFormat} pattern used to format numeric column values,
         * e.g. {@code "#,##0.00"} or {@code "#,##0.0000"}.
         */
        @JsonProperty("number-format")
        private String numberFormat;
        // Future: dateFormat, stringTransform, ...
    }

    public String checksum() {
        return checksum(Map.of());
    }

    /**
     * Compute a deterministic SHA-256 checksum of this config's full contents.
     * The object is serialized to JSON with stable ordering and inclusion settings
     * to ensure the same inputs always produce the same checksum.
     * The returned value is a lowercase hex string safe for use as a filename.
     */
    public String checksum(Map<String, Object> sharedParams) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            // deterministic property ordering
            mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
            mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
            // include nulls/empties so presence/absence is deterministic
            mapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
            // stable date/time formatting if any java.time types are present
            mapper.registerModule(new JavaTimeModule());
            mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

            byte[] bytes = mapper.writeValueAsBytes(this);
            byte[] sharedBytes = mapper.writeValueAsBytes(sharedParams);
            // combine both byte arrays for checksum
            byte[] combined = new byte[bytes.length + sharedBytes.length];
            System.arraycopy(bytes, 0, combined, 0, bytes.length);
            System.arraycopy(sharedBytes, 0, combined, bytes.length, sharedBytes.length);

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(combined);
            return bytesToHex(digest);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to compute checksum", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit((b & 0xF), 16));
        }
        return sb.toString();
    }
}
