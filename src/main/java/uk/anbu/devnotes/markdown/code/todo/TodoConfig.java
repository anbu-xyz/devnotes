package uk.anbu.devnotes.markdown.code.todo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TodoConfig {

    private ThresholdConfig thresholds;

    private FilterConfig filters;

    private List<TodoItem> items = List.of();

    @Data
    public static class ThresholdConfig {
        private ThresholdValues age              = new ThresholdValues();
        @JsonProperty("due-in")
        private DueInThresholdValues dueIn    = new DueInThresholdValues();
    }

    @Data
    public static class ThresholdValues {
        private int green = 7;
        private int amber = 14;
        private int red   = 30;
    }

    /**
     * Due-in threshold values.  Field names are self-consistent with the colour they represent:
     * {@code green} = "more than N days remaining → green",
     * {@code amber} = "more than N days remaining → amber",
     * {@code red}   = "more than N days remaining → red",
     * ≤ red → overdue.
     * Defaults mirror the age thresholds: green=30, amber=14, red=7.
     */
    @Data
    public static class DueInThresholdValues {
        private int green = 30;
        private int amber = 14;
        private int red   = 7;
    }

    /**
     * Filter visibility for the todo widget.
     * {@code null} (field absent) means visible; {@code false} means hidden.
     * Only {@code false} values are written to YAML (via NON_NULL serialisation),
     * keeping the file clean when all statuses are visible.
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FilterConfig {
        @JsonProperty("not-started")
        private Boolean notStarted;

        @JsonProperty("in-progress")
        private Boolean inProgress;

        private Boolean completed;

        /** {@code true} if items with status {@code not-started} should be shown. */
        public boolean isNotStartedVisible() { return !Boolean.FALSE.equals(notStarted); }

        /** {@code true} if items with status {@code in-progress} should be shown. */
        public boolean isInProgressVisible() { return !Boolean.FALSE.equals(inProgress); }

        /** {@code true} if items with status {@code completed} should be shown. */
        public boolean isCompletedVisible()  { return !Boolean.FALSE.equals(completed); }

        /** {@code true} if all three statuses are visible (i.e. no filters active). */
        public boolean isAllVisible() {
            return isNotStartedVisible() && isInProgressVisible() && isCompletedVisible();
        }
    }

    public enum TodoStatus {
        @JsonProperty("not-started") NOT_STARTED,
        @JsonProperty("in-progress") IN_PROGRESS,
        @JsonProperty("completed")   COMPLETED
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TodoItem {
        private String summary;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private LocalDate created;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private LocalDate due;

        private String description;

        private TodoStatus status;
    }
}