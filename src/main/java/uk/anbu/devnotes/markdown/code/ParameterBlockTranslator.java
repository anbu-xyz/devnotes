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

import static j2html.TagCreator.*;

@Slf4j
@RequiredArgsConstructor
public class ParameterBlockTranslator {
    private final ParameterRegistry parameterRegistry;

    @SneakyThrows
    public Node renderParameterBlock(String yaml) {
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        Map<String, Object> params;
        try {
            params = yamlMapper.readValue(yaml, new TypeReference<>() {});
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

        // Build a simple two-column table (key | value). No headers or footers.
        ContainerTag<?> tableTag = table();
        ContainerTag<?> tbodyTag = tbody();
        if (params != null && !params.isEmpty()) {
            for (Map.Entry<String, Object> e : params.entrySet()) {
                String key = e.getKey();
                Object value = e.getValue();
                String valueText = value == null ? "(null)" : value.toString();
                tbodyTag.with(tr().with(
                        td().withText(key),
                        td().withText(valueText)
                ));
            }
        }
        tableTag.with(tbodyTag);
        var html = new HtmlBlock();
        html.setLiteral(tableTag.render());
        return html;
    }
}
