package uk.anbu.devnotes.markdown.code.datablock;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

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
    private Output output;
    private Map<String, SqlParameter> parameters = Map.of();

    @Data
    public static class Output {
        @JsonProperty("template-type")
        private String templateType;
        private String template;
    }

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
}
