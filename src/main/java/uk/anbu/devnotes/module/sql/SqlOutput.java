package uk.anbu.devnotes.module.sql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class SqlOutput {
    private SqlInfo sql;
    private String datasourceName;
    private LocalDateTime executionTime;
    private int maxRowConfig;
    private List<MetadataColumn> metadata;
    private List<Map<String, Object>> data;
    private boolean dbHasMoreRowsThanMaxConfig;

    @Data
    @NoArgsConstructor
    public static class SqlInfo {
        private String sqlText;
        private Map<String, Object> parameterValues;
    }

    @Data
    @NoArgsConstructor
    public static class MetadataColumn {
        private String name;
        private String type;
    }

    public static SqlOutput fromJson(String jsonPath) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            return mapper.readValue(new File(jsonPath), SqlOutput.class);
        } catch (Exception e) {
            throw new RuntimeException("Error parsing JSON file: " + e.getMessage(), e);
        }
    }
}
