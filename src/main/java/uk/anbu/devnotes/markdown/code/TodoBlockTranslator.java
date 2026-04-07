package uk.anbu.devnotes.markdown.code;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import j2html.tags.ContainerTag;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.HtmlBlock;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import uk.anbu.devnotes.markdown.code.todo.TodoConfig;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static j2html.TagCreator.*;
import static uk.anbu.devnotes.util.FileBasedCache.generateHash;

@Slf4j
public class TodoBlockTranslator {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    public Optional<HtmlBlock> translate(String yaml) {
        try {
            var mapper = new ObjectMapper(new YAMLFactory()).registerModule(new JavaTimeModule());
            var config = mapper.readValue(yaml, TodoConfig.class);
            return Optional.of(buildHtmlBlock(config, yaml));
        } catch (Exception e) {
            log.error("Error parsing todo block", e);
            var errorBlock = new HtmlBlock();
            errorBlock.setLiteral(
                div().withClass("todo-error")
                     .withText("Error parsing todo YAML: " + e.getMessage())
                     .render()
            );
            return Optional.of(errorBlock);
        }
    }

    private HtmlBlock buildHtmlBlock(TodoConfig config, String rawYaml) {
        // Strip trailing whitespace so the hash is stable regardless of whether the
        // source file has a trailing newline before the closing fence.
        var todoId = generateHash(rawYaml.stripTrailing());

        // ── filter bar ────────────────────────────────────────────────────────
        var f = config.getFilters();
        ContainerTag<?> filterBar = div().withClass("todo-filters").with(
            span().withClass("todo-filters-label").withText("Show:"),
            makeFilterLabel("not-started", "Not started", f == null || f.isNotStartedVisible()),
            makeFilterLabel("in-progress", "In progress", f == null || f.isInProgressVisible()),
            makeFilterLabel("completed",   "Completed",   f == null || f.isCompletedVisible()),
            button().withType("button")
                    .withClass("todo-add-btn")
                    .withTitle("Add a new task")
                    .withText("+ Add")
        );

        // ── items ─────────────────────────────────────────────────────────────
        ContainerTag<?> todoBlock = div().withClass("todo-block");
        List<TodoConfig.TodoItem> items = config.getItems() != null ? config.getItems() : List.of();

        for (int idx = 0; idx < items.size(); idx++) {
            var item = items.get(idx);
            var thresholds = config.getThresholds() != null
                    ? config.getThresholds() : new TodoConfig.ThresholdConfig();
            String ageClass   = computeAgeClass(thresholds.getAge(), item);
            String dueInClass = computeDueInClass(thresholds.getDueIn(), item);
            String rowClass   = higherCriticality(ageClass, dueInClass);

            boolean isCompleted = item.getStatus() == TodoConfig.TodoStatus.COMPLETED;
            String itemClass = "todo-item " + rowClass + (isCompleted ? " todo-completed" : "");
            String statusKey = item.getStatus() != null
                    ? item.getStatus().name().toLowerCase().replace('_', '-') : "";

            ContainerTag<?> itemDiv = div()
                    .withClass(itemClass)
                    .attr("data-status", statusKey)
                    .attr("data-item-index", String.valueOf(idx))
                    .attr("data-item", toItemJson(item, statusKey));

            // Header row: advance button (left) | summary (middle) | edit button (right)
            ContainerTag<?> itemHeader = div().withClass("todo-item-header").with(
                button().withType("button")
                        .withClass("todo-next-state-btn")
                        .withTitle(nextStatusLabel(item.getStatus()))
                        .withText("→"),
                h3().withClass("todo-summary")
                    .withText(item.getSummary() != null ? item.getSummary() : ""),
                button().withType("button")
                        .withClass("todo-edit-btn")
                        .withTitle("Edit task")
                        .withText("✎")
            );
            itemDiv.with(itemHeader);

            boolean showOpenDays = !ageClass.equals("todo-green");
            boolean showDueIn    = item.getDue() != null;
            boolean showStatus   = item.getStatus() != null;

            if (showStatus || showOpenDays || showDueIn) {
                ContainerTag<?> metaDiv = div().withClass("todo-meta");
                if (showStatus) {
                    metaDiv.with(span().withClass("todo-status-badge todo-status-" + statusKey)
                                      .withText(statusLabel(item.getStatus())));
                }
                if (showOpenDays) {
                    metaDiv.with(small().withText("Open: " + computeOpenDays(item) + " days"));
                }
                if (showDueIn) {
                    metaDiv.with(small().withText("Due in: " + computeDueIn(item) + " days"));
                }
                itemDiv.with(metaDiv);
            }

            String descHtml = renderDescriptionMarkdown(item.getDescription());
            if (!descHtml.isBlank()) {
                itemDiv.with(div().withClass("todo-description").with(rawHtml(descHtml)));
            }
            todoBlock.with(itemDiv);
        }

        ContainerTag<?> widget = div()
                .withClass("todo-widget")
                .attr("data-todo-id", todoId)
                .with(filterBar, todoBlock);

        var block = new HtmlBlock();
        block.setLiteral(widget.render());
        return block;
    }

    private ContainerTag<?> makeFilterLabel(String statusKey, String labelText, boolean visible) {
        return label().withClass("todo-filter-label").with(
            input().withType("checkbox")
                   .withClass("todo-filter-cb")
                   .attr("data-filter-status", statusKey)
                   .condAttr(visible, "checked", "checked"),
            text(" " + labelText)
        );
    }

    private String nextStatusLabel(TodoConfig.TodoStatus current) {
        if (current == null) return "Set to Not started";
        return switch (current) {
            case NOT_STARTED -> "Advance to In progress";
            case IN_PROGRESS -> "Advance to Completed";
            case COMPLETED   -> "Reset to Not started";
        };
    }

    private String statusLabel(TodoConfig.TodoStatus status) {
        return switch (status) {
            case NOT_STARTED -> "Not started";
            case IN_PROGRESS -> "In progress";
            case COMPLETED   -> "Completed";
        };
    }

    /**
     * Returns the highest-criticality CSS class between the age-based and due-in-based
     * row classes.  Criticality ranking: todo-overdue > todo-red > todo-amber > todo-green.
     */
    String computeRowClass(TodoConfig config, TodoConfig.TodoItem item) {
        var thresholds  = config.getThresholds() != null ? config.getThresholds() : new TodoConfig.ThresholdConfig();
        String ageClass    = computeAgeClass(thresholds.getAge(), item);
        String dueInClass  = computeDueInClass(thresholds.getDueIn(), item);
        return higherCriticality(ageClass, dueInClass);
    }

    private String computeAgeClass(TodoConfig.ThresholdValues t, TodoConfig.TodoItem item) {
        if (item.getCreated() == null) return "todo-green";
        long daysOld = ChronoUnit.DAYS.between(item.getCreated(), LocalDate.now());
        if (daysOld < t.getGreen()) return "todo-green";
        if (daysOld < t.getAmber()) return "todo-amber";
        if (daysOld < t.getRed())   return "todo-red";
        return "todo-overdue";
    }

    private String computeDueInClass(TodoConfig.DueInThresholdValues t, TodoConfig.TodoItem item) {
        if (item.getDue() == null) return "todo-green";
        long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), item.getDue());
        if (daysLeft > t.getGreen()) return "todo-green";
        if (daysLeft > t.getAmber()) return "todo-amber";
        if (daysLeft > t.getRed())   return "todo-red";
        return "todo-overdue";
    }

    private String higherCriticality(String class1, String class2) {
        return criticality(class1) >= criticality(class2) ? class1 : class2;
    }

    private int criticality(String cssClass) {
        return switch (cssClass) {
            case "todo-overdue" -> 3;
            case "todo-red"     -> 2;
            case "todo-amber"   -> 1;
            default             -> 0;
        };
    }

    /**
     * Returns the number of days since {@code created} as a string,
     * or an em-dash if {@code created} is absent.
     */
    String computeOpenDays(TodoConfig.TodoItem item) {
        if (item.getCreated() == null) return "—";
        return String.valueOf(ChronoUnit.DAYS.between(item.getCreated(), LocalDate.now()));
    }

    /**
     * Returns the number of days until {@code due} as a string (negative = overdue),
     * or an em-dash if {@code due} is absent.
     */
    String computeDueIn(TodoConfig.TodoItem item) {
        if (item.getDue() == null) return "—";
        return String.valueOf(ChronoUnit.DAYS.between(LocalDate.now(), item.getDue()));
    }

    /**
     * Serialises the editable fields of {@code item} to a compact JSON string suitable
     * for the {@code data-item} HTML attribute.  The browser will decode any HTML entities
     * (e.g. {@code &quot;} → {@code "}) when JavaScript reads {@code el.dataset.item},
     * yielding valid JSON for {@code JSON.parse}.
     */
    private static String toItemJson(TodoConfig.TodoItem item, String statusKey) {
        try {
            return JSON_MAPPER.writeValueAsString(Map.of(
                    "summary",     item.getSummary()     != null ? item.getSummary()     : "",
                    "due",         item.getDue()         != null ? item.getDue().toString() : "",
                    "status",      statusKey,
                    "description", item.getDescription() != null ? item.getDescription() : ""
            ));
        } catch (Exception e) {
            log.warn("Failed to serialize todo item data for data-item attribute", e);
            return "{}";
        }
    }

    /**
     * Renders a CommonMark markdown string to HTML.
     * Returns an empty string for null/blank input.
     */
    String renderDescriptionMarkdown(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        var parser   = Parser.builder().build();
        var renderer = HtmlRenderer.builder().build();
        return renderer.render(parser.parse(markdown));
    }
}