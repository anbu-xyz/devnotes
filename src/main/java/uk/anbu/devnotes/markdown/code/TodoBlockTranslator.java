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
import java.util.Optional;

import static j2html.TagCreator.*;

@Slf4j
public class TodoBlockTranslator {
    
    public Optional<HtmlBlock> translate(String yaml) {
        try {
            var mapper = new ObjectMapper(new YAMLFactory()).registerModule(new JavaTimeModule());
            var config = mapper.readValue(yaml, TodoConfig.class);
            return Optional.of(buildHtmlBlock(config));
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

    private HtmlBlock buildHtmlBlock(TodoConfig config) {
        ContainerTag<?> wrapper = div().withClass("todo-block");
        List<TodoConfig.TodoItem> items = config.getItems() != null ? config.getItems() : List.of();
        for (var item : items) {
            var thresholds = config.getThresholds() != null ? config.getThresholds() : new TodoConfig.ThresholdConfig();
            String ageClass   = computeAgeClass(thresholds.getAge(), item);
            String dueInClass = computeDueInClass(thresholds.getDueIn(), item);
            String rowClass   = higherCriticality(ageClass, dueInClass);

            ContainerTag<?> itemDiv = div().withClass("todo-item " + rowClass);
            itemDiv.with(h3().withClass("todo-summary").withText(item.getSummary() != null ? item.getSummary() : ""));

            boolean showOpenDays = !ageClass.equals("todo-green");
            boolean showDueIn    = item.getDue() != null;

            if (showOpenDays || showDueIn) {
                ContainerTag<?> metaDiv = div().withClass("todo-meta");
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

            wrapper.with(itemDiv);
        }

        var block = new HtmlBlock();
        block.setLiteral(wrapper.render());
        return block;
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
        if (item.getCreated() == null) return "\u2014";
        return String.valueOf(ChronoUnit.DAYS.between(item.getCreated(), LocalDate.now()));
    }

    /**
     * Returns the number of days until {@code due} as a string (negative = overdue),
     * or an em-dash if {@code due} is absent.
     */
    String computeDueIn(TodoConfig.TodoItem item) {
        if (item.getDue() == null) return "\u2014";
        return String.valueOf(ChronoUnit.DAYS.between(LocalDate.now(), item.getDue()));
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