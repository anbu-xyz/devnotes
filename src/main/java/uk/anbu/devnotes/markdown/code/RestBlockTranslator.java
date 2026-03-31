package uk.anbu.devnotes.markdown.code;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import gg.jte.resolve.DirectoryCodeResolver;
import j2html.tags.ContainerTag;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.Node;
import uk.anbu.devnotes.markdown.code.restblock.RestCodeblockConfig;
import uk.anbu.devnotes.types.MarkdownFile;
import uk.anbu.devnotes.util.FileBasedCache;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static j2html.TagCreator.*;

@Slf4j
public class RestBlockTranslator {

    private static final HttpClient DEFAULT_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public Optional<Node> translate(String yaml, MarkdownFile markdownFile) {
        RestCodeblockConfig config;
        try {
            config = YAML_MAPPER.readValue(yaml, RestCodeblockConfig.class);
        } catch (Exception e) {
            log.error("Error parsing rest block YAML", e);
            return Optional.of(errorBlock("Error parsing YAML: " + e.getMessage()));
        }

        if (config.getUrl() == null || config.getUrl().isBlank()) {
            return Optional.of(errorBlock("'url' is required in rest block"));
        }

        var checksum = config.checksum();

        var cached = readCached(markdownFile, checksum);
        if (cached.isPresent()) {
            return cached;
        }

        String responseBody;
        try {
            responseBody = executeHttpRequest(config);
        } catch (RestBlockHttpException e) {
            log.warn("REST block HTTP error: {}", e.getMessage());
            return Optional.of(errorBlock(e.getMessage()));
        } catch (Exception e) {
            log.error("REST block network error", e);
            return Optional.of(errorBlock("Network error: " + e.getMessage()));
        }

        Object extracted;
        try {
            extracted = extractWithJsonPath(responseBody, config.getJsonpath());
        } catch (Exception e) {
            log.warn("REST block JSONPath error: {}", e.getMessage());
            return Optional.of(errorBlock("JSONPath error: " + e.getMessage()));
        }

        var rows = normalizeToRows(extracted);

        Node result;
        if (config.getOutput() != null
                && config.getOutput().getTemplate() != null
                && "jte".equalsIgnoreCase(config.getOutput().getTemplateType())) {
            result = buildFromTemplate(rows, config).orElse(errorBlock("Template rendering failed"));
        } else {
            result = buildHtmlTable(rows, config, checksum);
        }

        saveToCache(result, markdownFile, checksum);
        return Optional.of(result);
    }

    // -------------------------------------------------------------------------
    // HTTP
    // -------------------------------------------------------------------------

    private String executeHttpRequest(RestCodeblockConfig config) throws Exception {
        var client = config.isTlsVerify() ? DEFAULT_HTTP_CLIENT : buildTrustAllClient(config);

        var method = config.getMethod() == null ? "GET" : config.getMethod().toUpperCase();
        var timeout = Duration.ofSeconds(config.getTimeoutSeconds() > 0 ? config.getTimeoutSeconds() : 30);

        var requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(config.getUrl()))
                .timeout(timeout);

        // Add headers
        if (config.getHeaders() != null) {
            config.getHeaders().forEach(requestBuilder::header);
        }

        // Set method + body
        HttpRequest.BodyPublisher bodyPublisher = config.getBody() != null
                ? HttpRequest.BodyPublishers.ofString(config.getBody())
                : HttpRequest.BodyPublishers.noBody();

        switch (method) {
            case "POST"   -> requestBuilder.POST(bodyPublisher);
            case "PUT"    -> requestBuilder.PUT(bodyPublisher);
            case "DELETE" -> requestBuilder.DELETE();
            case "PATCH"  -> requestBuilder.method("PATCH", bodyPublisher);
            default       -> requestBuilder.GET();
        }

        var response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            var snippet = response.body() != null && response.body().length() > 200
                    ? response.body().substring(0, 200) + "…"
                    : response.body();
            throw new RestBlockHttpException("HTTP " + response.statusCode() + ": " + snippet);
        }

        return response.body();
    }

    private HttpClient buildTrustAllClient(RestCodeblockConfig config) {
        log.warn("REST block: tls-verify=false for url={} — certificate validation is disabled", config.getUrl());
        try {
            var trustAll = new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            }};
            var sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAll, null);
            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .connectTimeout(Duration.ofSeconds(config.getTimeoutSeconds() > 0 ? config.getTimeoutSeconds() : 30))
                    .build();
        } catch (Exception e) {
            log.error("Failed to build trust-all SSL context, falling back to default", e);
            return DEFAULT_HTTP_CLIENT;
        }
    }

    // -------------------------------------------------------------------------
    // JSON extraction
    // -------------------------------------------------------------------------

    Object extractWithJsonPath(String json, String jsonpath) throws Exception {
        if (jsonpath == null || jsonpath.isBlank()) {
            return JSON_MAPPER.readValue(json, Object.class);
        }
        try {
            return JsonPath.read(json, jsonpath);
        } catch (PathNotFoundException e) {
            throw new Exception("JSONPath '" + jsonpath + "' matched nothing: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new Exception("JSONPath evaluation failed: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Row normalisation
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> normalizeToRows(Object extracted) {
        if (extracted instanceof List<?> list) {
            if (list.isEmpty()) {
                return List.of();
            }
            if (list.get(0) instanceof Map) {
                // List<Map> — use directly
                List<Map<String, Object>> rows = new ArrayList<>();
                for (var item : list) {
                    if (item instanceof Map<?, ?> m) {
                        rows.add(toStringKeyMap(m));
                    } else {
                        // mixed list — wrap scalar
                        rows.add(Map.of("value", item == null ? "" : item.toString()));
                    }
                }
                return rows;
            } else {
                // List of scalars
                List<Map<String, Object>> rows = new ArrayList<>();
                for (var item : list) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("value", item);
                    rows.add(row);
                }
                return rows;
            }
        } else if (extracted instanceof Map<?, ?> m) {
            return List.of(toStringKeyMap(m));
        } else if (extracted == null) {
            return List.of();
        } else {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("value", extracted.toString());
            return List.of(row);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toStringKeyMap(Map<?, ?> m) {
        var result = new LinkedHashMap<String, Object>();
        m.forEach((k, v) -> result.put(k == null ? "null" : k.toString(), v));
        return result;
    }

    // -------------------------------------------------------------------------
    // HTML rendering
    // -------------------------------------------------------------------------

    private Node buildHtmlTable(List<Map<String, Object>> rows,
                                RestCodeblockConfig config,
                                String checksum) {
        if (rows.isEmpty()) {
            var wrapper = div().withClass("rest-block").attr("data-restblock-id", checksum)
                    .with(span().withText("(no results)"));
            return toHtmlBlock(wrapper);
        }

        // Determine columns
        List<String> columns;
        var options = config.getOptions();
        if (options != null && options.getColumns() != null && !options.getColumns().isEmpty()) {
            columns = new ArrayList<>(options.getColumns());
        } else {
            columns = new ArrayList<>(rows.get(0).keySet());
        }

        // Apply row limit
        int limit = (options != null && options.getRowLimit() > 0) ? options.getRowLimit() : 100;
        boolean limitReached = rows.size() > limit;
        var displayRows = limitReached ? rows.subList(0, limit) : rows;

        // Build thead
        var headerCells = columns.stream()
                .map(col -> th().withClass("rest-block-header").withText(col))
                .toArray(ContainerTag[]::new);
        var thead = thead().with(tr().with(headerCells));

        // Build tbody
        var tbodyRows = displayRows.stream().map(row -> {
            var cells = columns.stream()
                    .map(col -> createTdTag(col, row.get(col), config.getColumnFormats()))
                    .toArray(ContainerTag[]::new);
            return tr().with(cells);
        }).toArray(ContainerTag[]::new);
        var tbody = tbody().with(tbodyRows);

        // Build tfoot
        String footerText = limitReached
                ? String.format("Showing %d of more rows (limit reached)", limit)
                : String.format("Total: %d", displayRows.size());
        var tfoot = tfoot().with(
                tr().withClass("rest-block-status-row").with(
                        td().withClass("rest-block-status-cell")
                                .attr("colspan", String.valueOf(columns.size()))
                                .withText(footerText)
                )
        );

        var table = table().withClass("rest-block-table")
                .attr("data-restblock-id", checksum)
                .with(thead, tbody, tfoot);

        var wrapper = div().withClass("rest-block").with(table);
        return toHtmlBlock(wrapper);
    }

    private ContainerTag<?> createTdTag(String columnName,
                                        Object value,
                                        Map<String, RestCodeblockConfig.ColumnFormatConfig> columnFormats) {
        if (value == null) {
            return td().withText("(null)");
        }

        // Nested object → nested table
        if (value instanceof Map<?, ?> m) {
            return td().with(buildNestedMapTable(m));
        }
        if (value instanceof List<?> l) {
            return td().with(buildNestedListTable(l));
        }

        // Numeric formatting
        if (value instanceof Number num) {
            var fmt = findFormatConfig(columnName, columnFormats);
            if (fmt != null && fmt.getNumberFormat() != null) {
                try {
                    var df = new DecimalFormat(fmt.getNumberFormat());
                    return td().withClass("rest-block-number").withText(df.format(num));
                } catch (Exception e) {
                    log.warn("Invalid number-format pattern '{}' for column '{}'", fmt.getNumberFormat(), columnName);
                }
            }
            try {
                var df = new DecimalFormat("#,##0.##");
                return td().withClass("rest-block-number").withText(df.format(num));
            } catch (Exception e) {
                return td().withClass("rest-block-number").withText(num.toString());
            }
        }

        // Boolean or string
        return td().withText(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private ContainerTag<?> buildNestedMapTable(Map<?, ?> m) {
        var rows = m.entrySet().stream().map(e -> {
            var valDisplay = e.getValue() instanceof Map || e.getValue() instanceof List
                    ? writeJson(e.getValue())
                    : (e.getValue() == null ? "(null)" : String.valueOf(e.getValue()));
            return tr().with(
                    th().withText(e.getKey() == null ? "" : e.getKey().toString()),
                    td().withText(valDisplay)
            );
        }).toArray(ContainerTag[]::new);
        return table().withClass("rest-block-nested-table").with(tbody().with(rows));
    }

    private ContainerTag<?> buildNestedListTable(List<?> list) {
        var rows = new ArrayList<ContainerTag<?>>();
        for (int i = 0; i < list.size(); i++) {
            var item = list.get(i);
            var valDisplay = item instanceof Map || item instanceof List
                    ? writeJson(item)
                    : (item == null ? "(null)" : String.valueOf(item));
            rows.add(tr().with(
                    th().withText(String.valueOf(i)),
                    td().withText(valDisplay)
            ));
        }
        return table().withClass("rest-block-nested-table")
                .with(tbody().with(rows.toArray(new ContainerTag[0])));
    }

    private String writeJson(Object value) {
        try {
            return JSON_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private RestCodeblockConfig.ColumnFormatConfig findFormatConfig(
            String columnName,
            Map<String, RestCodeblockConfig.ColumnFormatConfig> formats) {
        if (formats == null || formats.isEmpty() || columnName == null) {
            return null;
        }
        for (var entry : formats.entrySet()) {
            if (columnName.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // JTE template rendering
    // -------------------------------------------------------------------------

    private Optional<Node> buildFromTemplate(List<Map<String, Object>> rows, RestCodeblockConfig config) {
        try {
            var tempDir = Files.createTempDirectory("jte-rest-templates");
            var codeResolver = new DirectoryCodeResolver(tempDir);
            var templateEngine = TemplateEngine.create(codeResolver, ContentType.Html);

            var templateName = "rest-user-template.jte";
            var templatePath = tempDir.resolve(templateName);
            Files.writeString(templatePath, config.getOutput().getTemplate());

            Map<String, Object> params = new HashMap<>();
            params.put("rows", rows);

            var output = new StringOutput();
            templateEngine.render(templateName, params, output);

            var html = new HtmlBlock();
            html.setLiteral(output.toString());
            return Optional.of(html);
        } catch (Exception e) {
            log.error("Error rendering rest block template", e);
            return Optional.of(errorBlock("Error rendering template: " + e.getMessage()));
        }
    }

    // -------------------------------------------------------------------------
    // Caching
    // -------------------------------------------------------------------------

    private Optional<Node> readCached(MarkdownFile markdownFile, String checksum) {
        if (markdownFile == null) {
            return Optional.empty();
        }
        try {
            var outputFile = cacheFilePath(markdownFile, checksum);
            if (outputFile.toFile().exists()) {
                var cached = FileBasedCache.readFromFile(outputFile, outputFile.getFileName().toString());
                var html = new HtmlBlock();
                html.setLiteral(cached);
                return Optional.of(html);
            }
        } catch (Exception e) {
            log.error("Error checking rest block cache", e);
        }
        return Optional.empty();
    }

    private void saveToCache(Node node, MarkdownFile markdownFile, String checksum) {
        if (node == null || markdownFile == null || !(node instanceof HtmlBlock hb)) {
            return;
        }
        try {
            var outputFile = cacheFilePath(markdownFile, checksum);
            FileBasedCache.saveOutput(outputFile, hb.getLiteral());
        } catch (Exception e) {
            log.error("Error saving rest block cache", e);
        }
    }

    public Path cacheFilePath(MarkdownFile markdownFile, String checksum) {
        var fileNameNoExt = markdownFile.fileName().replaceFirst("[.][^.]+$", "");
        var generatedFileName = fileNameNoExt + "." + checksum + ".output";
        return markdownFile.fullPath().getParent().resolve(generatedFileName);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private HtmlBlock errorBlock(String message) {
        var block = new HtmlBlock();
        block.setLiteral(div().withClass("rest-block-error").withText(message).render());
        return block;
    }

    private static HtmlBlock toHtmlBlock(ContainerTag<?> tag) {
        var block = new HtmlBlock();
        block.setLiteral(tag.render());
        return block;
    }

    // -------------------------------------------------------------------------
    // Exception
    // -------------------------------------------------------------------------

    static class RestBlockHttpException extends RuntimeException {
        RestBlockHttpException(String message) {
            super(message);
        }
    }
}