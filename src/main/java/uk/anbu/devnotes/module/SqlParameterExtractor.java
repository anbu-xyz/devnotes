package uk.anbu.devnotes.module;

import net.sf.jsqlparser.expression.JdbcNamedParameter;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.deparser.ExpressionDeParser;
import net.sf.jsqlparser.util.deparser.SelectDeParser;

import java.util.HashSet;
import java.util.Set;

public class SqlParameterExtractor {

    public static Set<String> extractPlaceholders(String sql) {
        Set<String> placeholders = new HashSet<>();

        try {
            Statement statement = CCJSqlParserUtil.parse(sql);

            ExpressionDeParser expressionDeParser = new ExpressionDeParser() {
                @Override
                public void visit(JdbcNamedParameter jdbcNamedParameter) {
                    placeholders.add(jdbcNamedParameter.getName());
                }
            };

            SelectDeParser selectDeParser = new SelectDeParser(expressionDeParser, new StringBuilder());
            expressionDeParser.setSelectVisitor(selectDeParser);

            if (statement instanceof Select selectStatement) {
                var plainSelect = (PlainSelect) selectStatement.getSelectBody();
                plainSelect.accept(selectDeParser);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return placeholders;
    }
}
