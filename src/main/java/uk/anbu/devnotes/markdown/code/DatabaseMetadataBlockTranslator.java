package uk.anbu.devnotes.markdown.code;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import j2html.tags.ContainerTag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.HtmlBlock;
import uk.anbu.devnotes.markdown.code.databasemetadata.DatabaseMetadataConfig;

import java.util.Map;
import java.util.Optional;

import static j2html.TagCreator.*;

@Slf4j
@RequiredArgsConstructor
public class DatabaseMetadataBlockTranslator {

    public Optional<HtmlBlock> translate(String yaml) {
        try {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            DatabaseMetadataConfig config = yamlMapper.readValue(yaml, DatabaseMetadataConfig.class);
            return Optional.of(buildHtmlBlock(config, yaml));
        } catch (Exception e) {
            log.error("Error parsing database-metadata block", e);
            HtmlBlock errorBlock = new HtmlBlock();
            errorBlock.setLiteral(
                div().withClass("database-metadata-error")
                    .withText("Error parsing database-metadata YAML: " + e.getMessage())
                    .render()
            );
            return Optional.of(errorBlock);
        }
    }

    private HtmlBlock buildHtmlBlock(DatabaseMetadataConfig config, String rawYaml) {
        DatabaseMetadataConfig.TableInfo table = config.getTable();
        String tableName = table != null && table.getName() != null ? table.getName() : "(unnamed)";
        String tableDesc = table != null && table.getDescription() != null ? table.getDescription() : "";
        String datasource = table != null ? table.getDatasource() : null;

        // Header row
        ContainerTag<?> headerRow = tr()
                .with(th().withText("Column"))
                .with(th().withText("Oracle Type"))
                .with(th().withText("H2 Type"))
                .with(th().withText("Java Type"))
                .with(th().withText("Description"))
                .with(th().withText("Values"));

        // Column rows
        ContainerTag<?> tbody = tbody();
        Map<String, DatabaseMetadataConfig.ColumnConfig> columns =
                config.getColumns() != null ? config.getColumns() : Map.of();

        for (Map.Entry<String, DatabaseMetadataConfig.ColumnConfig> entry : columns.entrySet()) {
            String colName = entry.getKey();
            DatabaseMetadataConfig.ColumnConfig col = entry.getValue();

            ContainerTag<?> valuesCell = buildValuesCell(col);

            tbody.with(tr()
                    .with(td().withClass("db-meta-col-name").withText(colName))
                    .with(td().withText(col.getOracleType() != null ? col.getOracleType() : ""))
                    .with(td().withText(col.getH2Type() != null ? col.getH2Type() : ""))
                    .with(td().withText(col.getJavaType() != null ? col.getJavaType() : ""))
                    .with(td().withText(col.getDescription() != null ? col.getDescription() : ""))
                    .with(valuesCell)
            );
        }

        ContainerTag<?> tableEl = table().withClass("db-meta-table")
                .with(thead().with(headerRow))
                .with(tbody);

        // Title bar
        ContainerTag<?> title = div().withClass("db-meta-title")
                .with(span().withClass("db-meta-table-name").withText("📋 " + tableName))
                .condWith(!tableDesc.isEmpty(),
                        span().withClass("db-meta-table-desc").withText(" · " + tableDesc));

        ContainerTag<?> wrapper = div().withClass("database-metadata-block")
                .with(title)
                .with(tableEl);

        // Optional "Check against DB" form
        if (datasource != null && !datasource.isBlank()) {
            String diffDivId = "db-meta-diff-" + tableName.replaceAll("[^a-zA-Z0-9_-]", "_");
            ContainerTag<?> form = form()
                    .attr("hx-post", "/database-metadata/check")
                    .attr("hx-target", "#" + diffDivId)
                    .attr("hx-swap", "innerHTML")
                    .with(input().withType("hidden").withName("datasource").withValue(datasource))
                    .with(input().withType("hidden").withName("yamlContent").withValue(rawYaml))
                    .with(button().withType("submit").withClass("db-meta-check-btn")
                            .withText("Check against DB ▶"));

            wrapper.with(form)
                    .with(div().withId(diffDivId).withClass("db-meta-diff-result"));
        }

        HtmlBlock block = new HtmlBlock();
        block.setLiteral(wrapper.render());
        return block;
    }

    private ContainerTag<?> buildValuesCell(DatabaseMetadataConfig.ColumnConfig col) {
        Map<String, String> values = col.getValues();
        if (values == null || values.isEmpty()) {
            return td();
        }
        ContainerTag<?> dl = dl().withClass("db-meta-values");
        for (Map.Entry<String, String> v : values.entrySet()) {
            dl.with(dt().withText(v.getKey()));
            dl.with(dd().withText(v.getValue()));
        }
        return td().with(dl);
    }
}

