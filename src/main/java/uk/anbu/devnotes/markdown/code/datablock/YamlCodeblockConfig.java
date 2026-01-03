package uk.anbu.devnotes.markdown.code.datablock;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class YamlCodeblockConfig {
    private String source;
    private String query;
    private String header;
    private Output output;

    // Map `options` in YAML to this object
    @JsonProperty("options")
    private SqlOptions options;
    @JsonProperty("hide-row-count")
    private boolean hideRowCount = false;
    @JsonProperty("hide-row-count-when-less-than")
    private int hideRowCountWhenLessThan = 10;

    @Data
    public static class Output {
        @JsonProperty("template-type")
        private String templateType;
        private String template;
    }

    @Data
    public static class SqlOptions {
        // YAML uses `limit` so map it to rowLimit
        @JsonProperty("limit")
        private int rowLimit = 100;
        private List<String> columns;
        @JsonProperty("columns-to-exclude")
        private List<String> columnsToExclude;
        @JsonProperty("dont-combine-single-column")
        private Boolean dontCombineSingleColumn;
    }
}
