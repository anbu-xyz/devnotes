package uk.anbu.devnotes;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.anbu.devnotes.module.GroovyExecutor;
import uk.anbu.devnotes.module.MarkdownRenderer;
import uk.anbu.devnotes.module.SqlExecutor;
import uk.anbu.devnotes.service.ConfigService;

@Configuration
public class DevnotesContext {
    @Bean
    MarkdownRenderer markdownRenderer(ConfigService configService,
                                      SqlExecutor sqlExecutor,
                                      GroovyExecutor groovyExecutor) {
        return new MarkdownRenderer(
                sqlExecutor::renderResultAsJsonFile,
                sqlExecutor::convertToHtmlTable,
                groovyExecutor::processGroovyCodeBlock,
                configService::getDataSourceConfig
        );
    }
}