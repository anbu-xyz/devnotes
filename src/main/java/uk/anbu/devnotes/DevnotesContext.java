package uk.anbu.devnotes;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.anbu.devnotes.markdown.code.DatabaseMetadataBlockTranslator;
import uk.anbu.devnotes.module.GroovyRenderer;
import uk.anbu.devnotes.module.MarkdownRenderer;
import uk.anbu.devnotes.module.sql.SqlExecutor;
import uk.anbu.devnotes.service.ConfigService;

@Configuration
public class DevnotesContext {
    @Bean
    MarkdownRenderer markdownRenderer(ConfigService configService,
                                      SqlExecutor sqlExecutor) {
        GroovyRenderer groovyExecutor = new GroovyRenderer(configService::getChromeDriverLocation);
        return new MarkdownRenderer(
                groovyExecutor,
                configService::getDataSourceConfig,
                sqlExecutor,
                configService,
                new DatabaseMetadataBlockTranslator()
        );
    }
}