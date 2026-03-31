package uk.anbu.devnotes.markdown.code.todo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TodoConfig {

    private ThresholdConfig thresholds = new ThresholdConfig();

    private List<TodoItem> items = List.of();

    @Data
    public static class ThresholdConfig {
        private ThresholdValues age      = new ThresholdValues();
        private ThresholdValues urgency  = new ThresholdValues();
    }

    @Data
    public static class ThresholdValues {
        private int green = 7;
        private int amber = 14;
        private int red   = 30;
    }

    @Data
    public static class TodoItem {
        private String summary;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private LocalDate created;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private LocalDate due;

        private String description;
    }
}