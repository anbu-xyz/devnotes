package uk.anbu.devnotes.markdown.code.datablock;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

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
}
