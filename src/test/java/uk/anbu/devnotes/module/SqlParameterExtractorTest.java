package uk.anbu.devnotes.module;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static uk.anbu.devnotes.module.SqlParameterExtractor.extractPlaceholders;

public class SqlParameterExtractorTest {

    @Test
    public void testExtractPlaceholders() {
        String sql = "select * from user where name = :user_name and age = :user_age";

        Set<String> placeholders = extractPlaceholders(sql);

        Assertions.assertThat(placeholders).hasSize(2);
        Assertions.assertThat(placeholders).contains("user_name", "user_age");
    }

}
