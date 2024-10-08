package uk.anbu.devnotes.module;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class SqlResult {
    private Sql sql;
    private LocalDateTime executionTime;
    private String datasourceName;
    private boolean hasReachedMaxRows;
    private Data data;

    public record Sql(String sqlText, Map<String, Object> parameters) {
    }
    public record Metadata(String name, String javaClass) {
    }
    public record Data(List<Metadata> metadata, List<Map<String, Object>> rowData) {};

    public int getRowCount() {
        return data.rowData().size();
    }

    public boolean hasReachedMaxRows() {
        return hasReachedMaxRows;
    }

}
