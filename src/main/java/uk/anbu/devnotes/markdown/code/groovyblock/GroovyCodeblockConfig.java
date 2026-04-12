package uk.anbu.devnotes.markdown.code.groovyblock;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * YAML configuration POJO for fenced {@code ```groovy} code blocks.
 *
 * <p>The block body uses a YAML header separated from the Groovy script by {@code ---}:
 * <pre>{@code
 * ```groovy
 * output: html
 * cache-enabled: false
 * controls-enabled: true
 * ---
 * println "<div>Hello</div>"
 * ```
 * }</pre>
 *
 * <p>All fields are optional; sensible defaults are applied when absent.
 */
@Data
public class GroovyCodeblockConfig {

    /**
     * The output rendering type. Accepted values:
     * {@code html}, {@code text}, {@code code-block},
     * {@code csv-table}, {@code csv-table-with-header}.
     * Defaults to {@code html}.
     */
    private String output = "html";

    /**
     * When {@code false} the script result is never cached to disk.
     * Default: {@code true}.
     */
    @JsonProperty("cache-enabled")
    private boolean cacheEnabled = true;

    /**
     * When {@code false} the ⋮ context-menu button is suppressed.
     * Default: {@code true}.
     */
    @JsonProperty("controls-enabled")
    private boolean controlsEnabled = true;
}

