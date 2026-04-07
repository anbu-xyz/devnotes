package uk.anbu.devnotes.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.HtmlBlock;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devnotes.markdown.code.TodoBlockTranslator;
import uk.anbu.devnotes.markdown.code.todo.TodoConfig;
import uk.anbu.devnotes.service.ConfigService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static uk.anbu.devnotes.util.FileBasedCache.generateHash;

@RestController
@RequestMapping("/todo")
@RequiredArgsConstructor
@Slf4j
public class TodoStatusController {

    private static final Pattern TODO_FENCE =
            Pattern.compile("(```todo[^\\r\\n]*\\r?\\n)(.*?)(\\r?\\n[ \\t]*```)", Pattern.DOTALL);

    private final ConfigService configService;

    /**
     * Advances the status of a single todo item and returns the re-rendered widget HTML.
     * Body: {@code { markdownFile, todoId, itemIndex }}.
     */
    @PostMapping(value = "/advance-status",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> advanceStatus(@RequestBody Map<String, Object> body) {
        var markdownFileOpt = Optional.ofNullable(body.get("markdownFile"))
                .map(Object::toString).filter(s -> !s.isBlank());
        var todoIdOpt = Optional.ofNullable(body.get("todoId"))
                .map(Object::toString).filter(s -> !s.isBlank());
        var itemIndexOpt = Optional.ofNullable(body.get("itemIndex"))
                .map(Object::toString);

        if (markdownFileOpt.isEmpty() || todoIdOpt.isEmpty() || itemIndexOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("missing markdownFile, todoId, or itemIndex");
        }

        int itemIndex;
        try {
            itemIndex = Integer.parseInt(itemIndexOpt.get());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body("itemIndex must be an integer");
        }

        try {
            var mdPathOpt = resolveMdPath(markdownFileOpt.get());
            if (mdPathOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("markdown file not found: " + markdownFileOpt.get());
            }

            var content = Files.readString(mdPathOpt.get());
            var result = updateTodoStatus(content, todoIdOpt.get(), itemIndex);
            if (result.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("todo block not found: " + todoIdOpt.get());
            }

            Files.writeString(mdPathOpt.get(), result.get().updatedMarkdown());

            var html = new TodoBlockTranslator()
                    .translate(result.get().newYaml())
                    .map(HtmlBlock::getLiteral);

            return html.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.internalServerError().body("failed to re-render todo block"));
        } catch (Exception e) {
            log.error("Error advancing todo item status", e);
            return ResponseEntity.internalServerError().body("error: " + e.getMessage());
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Optional<Path> resolveMdPath(String markdownFileStr) {
        var mdPath = Paths.get(configService.getDocsDirectory()).resolve(markdownFileStr);
        if (Files.exists(mdPath) && Files.isRegularFile(mdPath)) {
            return Optional.of(mdPath);
        }
        return Optional.empty();
    }

    /**
     * Finds the {@code ```todo} fence whose YAML body hash matches {@code todoId},
     * advances the status of item at {@code itemIndex}, re-serialises the YAML, and
     * substitutes it back into the full markdown string.
     *
     * @return the updated markdown content + new YAML body, or empty if no matching block found
     */
    private Optional<UpdateResult> updateTodoStatus(String content, String todoId, int itemIndex)
            throws Exception {
        var matcher = TODO_FENCE.matcher(content);
        var sb = new StringBuilder();
        String newYaml = null;

        while (matcher.find()) {
            var yamlBody = matcher.group(2);
            if (generateHash(yamlBody.stripTrailing()).equals(todoId)) {
                newYaml = advanceItemStatus(yamlBody, itemIndex);
                // stripTrailing() prevents an extra blank line appearing before the
                // closing fence when Jackson appends a trailing newline.
                matcher.appendReplacement(sb,
                        Matcher.quoteReplacement(matcher.group(1) + newYaml.stripTrailing() + matcher.group(3)));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(sb);

        return newYaml != null ? Optional.of(new UpdateResult(sb.toString(), newYaml)) : Optional.empty();
    }

    /**
     * Parses {@code yamlBody}, advances the status of item at {@code itemIndex},
     * and re-serialises to YAML (null fields omitted, no doc-start marker).
     */
    private String advanceItemStatus(String yamlBody, int itemIndex) throws Exception {
        var mapper = buildMapper();
        var config = mapper.readValue(yamlBody, TodoConfig.class);
        var items = config.getItems();

        if (itemIndex < 0 || itemIndex >= items.size()) {
            throw new IllegalArgumentException("itemIndex out of range: " + itemIndex);
        }

        var item = items.get(itemIndex);
        item.setStatus(nextStatus(item.getStatus()));

        return mapper.writeValueAsString(config);
    }

    private TodoConfig.TodoStatus nextStatus(TodoConfig.TodoStatus current) {
        if (current == null) return TodoConfig.TodoStatus.NOT_STARTED;
        return switch (current) {
            case NOT_STARTED -> TodoConfig.TodoStatus.IN_PROGRESS;
            case IN_PROGRESS -> TodoConfig.TodoStatus.COMPLETED;
            case COMPLETED   -> TodoConfig.TodoStatus.NOT_STARTED;
        };
    }

    // ── add-item ──────────────────────────────────────────────────────────────

    /**
     * Prepends a new item to the top of a todo block and returns the re-rendered widget HTML.
     * Body: {@code { markdownFile, todoId, summary, due?, description?, status? }}.
     * The {@code created} date is always set to today (UTC).
     */
    @PostMapping(value = "/add-item",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> addItem(@RequestBody Map<String, Object> body) {
        var markdownFileOpt = Optional.ofNullable(body.get("markdownFile"))
                .map(Object::toString).filter(s -> !s.isBlank());
        var todoIdOpt = Optional.ofNullable(body.get("todoId"))
                .map(Object::toString).filter(s -> !s.isBlank());
        var summaryOpt = Optional.ofNullable(body.get("summary"))
                .map(Object::toString).filter(s -> !s.isBlank());

        if (markdownFileOpt.isEmpty() || todoIdOpt.isEmpty() || summaryOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("missing markdownFile, todoId, or summary");
        }

        var dueStr = Optional.ofNullable(body.get("due"))
                .map(Object::toString).filter(s -> !s.isBlank()).orElse(null);
        var description = Optional.ofNullable(body.get("description"))
                .map(Object::toString).filter(s -> !s.isBlank()).orElse(null);
        var statusStr = Optional.ofNullable(body.get("status"))
                .map(Object::toString).filter(s -> !s.isBlank()).orElse(null);

        try {
            var mdPathOpt = resolveMdPath(markdownFileOpt.get());
            if (mdPathOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("markdown file not found: " + markdownFileOpt.get());
            }

            var content = Files.readString(mdPathOpt.get());
            var result = prependTodoItem(content, todoIdOpt.get(),
                    summaryOpt.get(), dueStr, description, statusStr);
            if (result.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("todo block not found: " + todoIdOpt.get());
            }

            Files.writeString(mdPathOpt.get(), result.get().updatedMarkdown());

            var html = new TodoBlockTranslator()
                    .translate(result.get().newYaml())
                    .map(HtmlBlock::getLiteral);

            return html.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.internalServerError().body("failed to re-render todo block"));
        } catch (Exception e) {
            log.error("Error adding todo item", e);
            return ResponseEntity.internalServerError().body("error: " + e.getMessage());
        }
    }

    private Optional<UpdateResult> prependTodoItem(String content, String todoId,
                                                    String summary, String dueStr,
                                                    String description, String statusStr)
            throws Exception {
        var matcher = TODO_FENCE.matcher(content);
        var sb = new StringBuilder();
        String newYaml = null;

        while (matcher.find()) {
            var yamlBody = matcher.group(2);
            if (generateHash(yamlBody.stripTrailing()).equals(todoId)) {
                newYaml = buildItemPrependedYaml(yamlBody, summary, dueStr, description, statusStr);
                matcher.appendReplacement(sb,
                        Matcher.quoteReplacement(matcher.group(1) + newYaml.stripTrailing() + matcher.group(3)));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(sb);

        return newYaml != null ? Optional.of(new UpdateResult(sb.toString(), newYaml)) : Optional.empty();
    }

    private String buildItemPrependedYaml(String yamlBody, String summary, String dueStr,
                                           String description, String statusStr) throws Exception {
        var mapper = buildMapper();
        var config = mapper.readValue(yamlBody, TodoConfig.class);

        var newItem = new TodoConfig.TodoItem();
        newItem.setSummary(summary);
        newItem.setCreated(LocalDate.now());
        if (dueStr != null) {
            newItem.setDue(LocalDate.parse(dueStr));
        }
        if (description != null) {
            newItem.setDescription(description);
        }
        if (statusStr != null) {
            TodoConfig.TodoStatus status = switch (statusStr) {
                case "in-progress" -> TodoConfig.TodoStatus.IN_PROGRESS;
                case "completed"   -> TodoConfig.TodoStatus.COMPLETED;
                default            -> TodoConfig.TodoStatus.NOT_STARTED;
            };
            newItem.setStatus(status);
        }

        var items = new ArrayList<TodoConfig.TodoItem>();
        items.add(newItem);
        if (config.getItems() != null) {
            items.addAll(config.getItems());
        }
        config.setItems(items);
        return mapper.writeValueAsString(config);
    }

    // ── edit-item ─────────────────────────────────────────────────────────────

    /**
     * Updates an existing todo item in place and returns the re-rendered widget HTML.
     * Body: {@code { markdownFile, todoId, itemIndex, summary, due?, description?, status? }}.
     * The {@code created} date of the item is preserved unchanged.
     */
    @PostMapping(value = "/edit-item",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> editItem(@RequestBody Map<String, Object> body) {
        var markdownFileOpt = Optional.ofNullable(body.get("markdownFile"))
                .map(Object::toString).filter(s -> !s.isBlank());
        var todoIdOpt = Optional.ofNullable(body.get("todoId"))
                .map(Object::toString).filter(s -> !s.isBlank());
        var summaryOpt = Optional.ofNullable(body.get("summary"))
                .map(Object::toString).filter(s -> !s.isBlank());
        var itemIndexOpt = Optional.ofNullable(body.get("itemIndex"))
                .map(Object::toString);

        if (markdownFileOpt.isEmpty() || todoIdOpt.isEmpty() || summaryOpt.isEmpty() || itemIndexOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("missing markdownFile, todoId, summary, or itemIndex");
        }

        int itemIndex;
        try {
            itemIndex = Integer.parseInt(itemIndexOpt.get());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body("itemIndex must be an integer");
        }

        var dueStr = Optional.ofNullable(body.get("due"))
                .map(Object::toString).filter(s -> !s.isBlank()).orElse(null);
        var description = Optional.ofNullable(body.get("description"))
                .map(Object::toString).filter(s -> !s.isBlank()).orElse(null);
        var statusStr = Optional.ofNullable(body.get("status"))
                .map(Object::toString).filter(s -> !s.isBlank()).orElse(null);

        try {
            var mdPathOpt = resolveMdPath(markdownFileOpt.get());
            if (mdPathOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("markdown file not found: " + markdownFileOpt.get());
            }

            var content = Files.readString(mdPathOpt.get());
            var result = updateTodoItem(content, todoIdOpt.get(), itemIndex,
                    summaryOpt.get(), dueStr, description, statusStr);
            if (result.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("todo block not found: " + todoIdOpt.get());
            }

            Files.writeString(mdPathOpt.get(), result.get().updatedMarkdown());

            var html = new TodoBlockTranslator()
                    .translate(result.get().newYaml())
                    .map(HtmlBlock::getLiteral);

            return html.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.internalServerError().body("failed to re-render todo block"));
        } catch (Exception e) {
            log.error("Error editing todo item", e);
            return ResponseEntity.internalServerError().body("error: " + e.getMessage());
        }
    }

    private Optional<UpdateResult> updateTodoItem(String content, String todoId, int itemIndex,
                                                   String summary, String dueStr,
                                                   String description, String statusStr)
            throws Exception {
        var matcher = TODO_FENCE.matcher(content);
        var sb = new StringBuilder();
        String newYaml = null;

        while (matcher.find()) {
            var yamlBody = matcher.group(2);
            if (generateHash(yamlBody.stripTrailing()).equals(todoId)) {
                newYaml = buildItemEditedYaml(yamlBody, itemIndex, summary, dueStr, description, statusStr);
                matcher.appendReplacement(sb,
                        Matcher.quoteReplacement(matcher.group(1) + newYaml.stripTrailing() + matcher.group(3)));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(sb);

        return newYaml != null ? Optional.of(new UpdateResult(sb.toString(), newYaml)) : Optional.empty();
    }

    private String buildItemEditedYaml(String yamlBody, int itemIndex, String summary,
                                        String dueStr, String description,
                                        String statusStr) throws Exception {
        var mapper = buildMapper();
        var config = mapper.readValue(yamlBody, TodoConfig.class);
        var items = config.getItems();

        if (itemIndex < 0 || itemIndex >= items.size()) {
            throw new IllegalArgumentException("itemIndex out of range: " + itemIndex);
        }

        var item = items.get(itemIndex);
        item.setSummary(summary);
        item.setDue(dueStr != null ? LocalDate.parse(dueStr) : null);
        item.setDescription(description);
        if (statusStr != null) {
            TodoConfig.TodoStatus status = switch (statusStr) {
                case "in-progress" -> TodoConfig.TodoStatus.IN_PROGRESS;
                case "completed"   -> TodoConfig.TodoStatus.COMPLETED;
                default            -> TodoConfig.TodoStatus.NOT_STARTED;
            };
            item.setStatus(status);
        } else {
            item.setStatus(null);
        }

        return mapper.writeValueAsString(config);
    }

    // ── save-filters ──────────────────────────────────────────────────────────

    /**
     * Persists the filter-checkbox state into the todo block's YAML and returns
     * the new {@code data-todo-id} hash (plain text) so the browser can update the
     * widget's data attribute without a full DOM swap.
     * <p>
     * Body: {@code { markdownFile, todoId, "not-started": bool, "in-progress": bool, "completed": bool }}.
     */
    @PostMapping(value = "/save-filters",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> saveFilters(@RequestBody Map<String, Object> body) {
        var markdownFileOpt = Optional.ofNullable(body.get("markdownFile"))
                .map(Object::toString).filter(s -> !s.isBlank());
        var todoIdOpt = Optional.ofNullable(body.get("todoId"))
                .map(Object::toString).filter(s -> !s.isBlank());

        if (markdownFileOpt.isEmpty() || todoIdOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("missing markdownFile or todoId");
        }

        // null / missing values default to true (visible)
        boolean notStarted = !Boolean.FALSE.equals(body.get("not-started"));
        boolean inProgress = !Boolean.FALSE.equals(body.get("in-progress"));
        boolean completed  = !Boolean.FALSE.equals(body.get("completed"));

        try {
            var mdPathOpt = resolveMdPath(markdownFileOpt.get());
            if (mdPathOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("markdown file not found: " + markdownFileOpt.get());
            }

            var content = Files.readString(mdPathOpt.get());
            var result = updateFilters(content, todoIdOpt.get(), notStarted, inProgress, completed);
            if (result.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("todo block not found: " + todoIdOpt.get());
            }

            Files.writeString(mdPathOpt.get(), result.get().updatedMarkdown());
            return ResponseEntity.ok(result.get().newTodoId());
        } catch (Exception e) {
            log.error("Error saving todo filters", e);
            return ResponseEntity.internalServerError().body("error: " + e.getMessage());
        }
    }

    private Optional<FilterUpdateResult> updateFilters(String content, String todoId,
                                                        boolean notStarted, boolean inProgress,
                                                        boolean completed) throws Exception {
        var matcher = TODO_FENCE.matcher(content);
        var sb = new StringBuilder();
        String newTodoId = null;

        while (matcher.find()) {
            var yamlBody = matcher.group(2);
            if (generateHash(yamlBody.stripTrailing()).equals(todoId)) {
                var newYaml = updateConfigFilters(yamlBody, notStarted, inProgress, completed);
                newTodoId = generateHash(newYaml.stripTrailing());
                matcher.appendReplacement(sb,
                        Matcher.quoteReplacement(matcher.group(1) + newYaml.stripTrailing() + matcher.group(3)));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(sb);

        return newTodoId != null
                ? Optional.of(new FilterUpdateResult(sb.toString(), newTodoId))
                : Optional.empty();
    }

    /**
     * Parses {@code yamlBody}, sets the {@code filters} section according to the
     * given visibility flags, and re-serialises. When all are visible the
     * {@code filters} key is omitted entirely, keeping the YAML clean.
     */
    private String updateConfigFilters(String yamlBody,
                                       boolean notStarted, boolean inProgress,
                                       boolean completed) throws Exception {
        var mapper = buildMapper();
        var config = mapper.readValue(yamlBody, TodoConfig.class);

        if (notStarted && inProgress && completed) {
            config.setFilters(null);
        } else {
            var f = new TodoConfig.FilterConfig();
            f.setNotStarted(!notStarted ? Boolean.FALSE : null);
            f.setInProgress(!inProgress ? Boolean.FALSE : null);
            f.setCompleted(!completed   ? Boolean.FALSE : null);
            config.setFilters(f);
        }

        return mapper.writeValueAsString(config);
    }

    private record FilterUpdateResult(String updatedMarkdown, String newTodoId) {}

    private ObjectMapper buildMapper() {
        var factory = new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
        return new ObjectMapper(factory)
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    private record UpdateResult(String updatedMarkdown, String newYaml) {}
}