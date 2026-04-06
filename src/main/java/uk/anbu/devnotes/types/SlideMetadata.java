package uk.anbu.devnotes.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public record SlideMetadata(
        String title,
        String cssClass,
        String background,
        String notes,
        String layout
) {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    public static SlideMetadata empty() {
        return new SlideMetadata(null, null, null, null, null);
    }

    /**
     * Attempts to parse a YAML string as per-slide metadata.
     * Returns {@link Optional#empty()} if the text is blank, fails to parse,
     * or contains none of the recognised slide-metadata fields.
     */
    public static Optional<SlideMetadata> tryParse(String yamlText) {
        if (yamlText == null || yamlText.isBlank()) {
            return Optional.empty();
        }
        try {
            var pojo = YAML_MAPPER.readValue(yamlText.trim(), Pojo.class);
            if (pojo.title == null && pojo.cssClass == null && pojo.background == null
                    && pojo.notes == null && pojo.layout == null) {
                return Optional.empty();
            }
            return Optional.of(new SlideMetadata(pojo.title, pojo.cssClass, pojo.background, pojo.notes, pojo.layout));
        } catch (JsonProcessingException e) {
            log.debug("Per-slide YAML parse failed (treating as content): {}", e.getMessage());
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Pojo {
        public String title;
        @JsonProperty("class")
        public String cssClass;
        public String background;
        public String notes;
        public String layout;
    }
}