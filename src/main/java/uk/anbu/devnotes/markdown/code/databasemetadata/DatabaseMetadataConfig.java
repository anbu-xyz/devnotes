package uk.anbu.devnotes.markdown.code.databasemetadata;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class DatabaseMetadataConfig {

    private TableInfo table;
    private Map<String, ColumnConfig> columns = new LinkedHashMap<>();

    @Data
    public static class TableInfo {
        private String name;
        private String description;
        private String datasource;
    }

    @Data
    public static class ColumnConfig {
        @JsonProperty("oracle-type")
        private String oracleType;

        @JsonProperty("h2-type")
        private String h2Type;

        @JsonProperty("java-type")
        private String javaType;

        private String description;

        /** Allowed values and their meanings, e.g. { "PRP": "Perpetual bond" } */
        private Map<String, String> values = new LinkedHashMap<>();
    }
}

