package uk.anbu.devnotes.module.sql;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static uk.anbu.devnotes.module.sql.SqlParameterExtractor.extractPlaceholders;

public class SqlParameterExtractorTest {

    @Test
    public void testExtractPlaceholders() {
        String sql = "select * from user where name = :user_name and age = :user_age";

        Set<String> placeholders = extractPlaceholders(sql);

        Assertions.assertThat(placeholders).hasSize(2);
        Assertions.assertThat(placeholders).contains("user_name", "user_age");
    }

}
