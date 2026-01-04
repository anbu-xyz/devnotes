package uk.anbu.devnotes.markdown.code;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import j2html.tags.ContainerTag;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.Node;
import uk.anbu.devnotes.markdown.code.datablock.ParameterRegistry;

import java.util.Map;

import static j2html.TagCreator.div;

@Slf4j
@RequiredArgsConstructor
public class ParameterBlockTranslator {
    private final ParameterRegistry parameterRegistry;

    @SneakyThrows
    public Node renderParameterBlock(String yaml) {
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        Map<String, Object> params;
        try {
            params = yamlMapper.readValue(yaml, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            ContainerTag<?> err = div().withText("Error parsing parameter block YAML: " + e.getMessage());
            var html = new HtmlBlock();
            html.setLiteral(err.render());
            return html;
        }

        if (params != null && !params.isEmpty()) {
            for (Map.Entry<String, Object> e : params.entrySet()) {
                String key = e.getKey();
                Object value = e.getValue();
                if (parameterRegistry.get(key) == null) {
                    parameterRegistry.put(key, value);
                }
            }
        }

        ContainerTag<?> ok = div().withText("Loaded " + (params == null ? 0 : params.size()) + " parameters");
        var html = new HtmlBlock();
        html.setLiteral(ok.render());
        return html;
    }
}
