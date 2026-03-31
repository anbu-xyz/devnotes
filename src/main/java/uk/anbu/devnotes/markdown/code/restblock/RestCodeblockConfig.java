package uk.anbu.devnotes.markdown.code.restblock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Data;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RestCodeblockConfig {

    private String url;
    private String method = "GET";
    private Map<String, String> headers = Map.of();
    private String body;

    @JsonProperty("timeout-seconds")
    private int timeoutSeconds = 30;

    @JsonProperty("tls-verify")
    private boolean tlsVerify = true;

    private String jsonpath;
    private RestOptions options;

    @JsonProperty("column-formats")
    private Map<String, ColumnFormatConfig> columnFormats = Map.of();

    private Output output;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RestOptions {
        @JsonProperty("row-limit")
        private int rowLimit = 100;
        private List<String> columns;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ColumnFormatConfig {
        @JsonProperty("number-format")
        private String numberFormat;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Output {
        @JsonProperty("template-type")
        private String templateType;
        private String template;
    }

    /**
     * Compute a deterministic SHA-256 checksum of this config's full contents.
     * The returned value is a lowercase hex string safe for use as a filename.
     */
    public String checksum() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
            mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
            mapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
            mapper.registerModule(new JavaTimeModule());
            mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

            byte[] bytes = mapper.writeValueAsBytes(this);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
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