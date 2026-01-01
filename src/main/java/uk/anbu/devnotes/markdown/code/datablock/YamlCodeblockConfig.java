package uk.anbu.devnotes.markdown.code.datablock;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class YamlCodeblockConfig {
    private String source;
    private String query;
    private Output output;

    // Map `options` in YAML to this object
    @JsonProperty("options")
    private SqlOptions options;

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
        private int rowLimit;
        private List<String> columns;
        @JsonProperty("columns-to-exclude")
        private List<String> columnsToExclude;
        @JsonProperty("dont-combine-single-column")
        private Boolean dontCombineSingleColumn;
    }
}
