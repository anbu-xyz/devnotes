package uk.anbu.devnotes.util

import spock.lang.Specification
import spock.lang.Unroll

class JdbcTypeMapperSpec extends Specification {

    // -------------------------------------------------------------------------
    // resolveTypeFieldName
    // -------------------------------------------------------------------------

    @Unroll
    def "resolveTypeFieldName('#dbProduct') returns '#expected'"() {
        expect:
        JdbcTypeMapper.resolveTypeFieldName(dbProduct) == expected

        where:
        dbProduct    || expected
        "H2"         || "h2-type"
        "h2"         || "h2-type"
        "Oracle"     || "oracle-type"
        "ORACLE"     || "oracle-type"
        "PostgreSQL" || "db-type"
        "MySQL"      || "db-type"
        "SQL Server" || "db-type"
        null         || "db-type"
    }

    // -------------------------------------------------------------------------
    // formatType
    // -------------------------------------------------------------------------

    @Unroll
    def "formatType suppresses size for integer type '#typeName'"() {
        expect:
        JdbcTypeMapper.formatType("H2", typeName, 10, 0) == typeName

        where:
        typeName << ["INTEGER", "INT", "SMALLINT", "TINYINT", "BIGINT"]
    }

    def "formatType suppresses size for BOOLEAN"() {
        expect:
        JdbcTypeMapper.formatType("H2", "BOOLEAN", 1, 0) == "BOOLEAN"
    }

    def "formatType includes size for CHARACTER LARGE OBJECT (H2 CLOB)"() {
        // H2 reports CLOB as CHARACTER LARGE OBJECT with a very large COLUMN_SIZE;
        // the formatter should include it so the user sees the raw metadata faithfully
        expect:
        JdbcTypeMapper.formatType("H2", "CHARACTER LARGE OBJECT", 1_000_000_000, 0) ==
                "CHARACTER LARGE OBJECT(1000000000)"
    }

    def "formatType appends size for VARCHAR"() {
        expect:
        JdbcTypeMapper.formatType("H2", "VARCHAR", 200, 0) == "VARCHAR(200)"
    }

    def "formatType appends size for CHARACTER VARYING (H2 2.x)"() {
        expect:
        JdbcTypeMapper.formatType("H2", "CHARACTER VARYING", 200, 0) == "CHARACTER VARYING(200)"
    }

    def "formatType appends size and digits for DECIMAL"() {
        expect:
        JdbcTypeMapper.formatType("H2", "DECIMAL", 10, 2) == "DECIMAL(10,2)"
    }

    def "formatType appends size and digits for Oracle NUMBER"() {
        expect:
        JdbcTypeMapper.formatType("Oracle", "NUMBER", 10, 2) == "NUMBER(10,2)"
    }

    def "formatType appends only size when digits is 0 for Oracle VARCHAR2"() {
        expect:
        JdbcTypeMapper.formatType("Oracle", "VARCHAR2", 200, 0) == "VARCHAR2(200)"
    }

    def "formatType returns bare type name when size is 0"() {
        expect:
        JdbcTypeMapper.formatType("PostgreSQL", "TEXT", 0, 0) == "TEXT"
    }

    def "formatType handles null dbProductName"() {
        expect:
        JdbcTypeMapper.formatType(null, "VARCHAR", 50, 0) == "VARCHAR(50)"
    }

    def "formatType returns empty string for null typeName"() {
        expect:
        JdbcTypeMapper.formatType("H2", null, 10, 0) == ""
    }

    // -------------------------------------------------------------------------
    // toJavaType
    // -------------------------------------------------------------------------

    @Unroll
    def "toJavaType('#typeName') returns '#expected'"() {
        expect:
        JdbcTypeMapper.toJavaType(typeName) == expected

        where:
        typeName              || expected
        "INTEGER"             || "java.lang.Integer"
        "INT"                 || "java.lang.Integer"
        "SMALLINT"            || "java.lang.Integer"
        "TINYINT"             || "java.lang.Integer"
        "BIGINT"              || "java.lang.Long"
        "FLOAT"               || "java.lang.Float"
        "REAL"                || "java.lang.Float"
        "DOUBLE"              || "java.lang.Double"
        "DOUBLE PRECISION"    || "java.lang.Double"
        "DECIMAL"             || "java.math.BigDecimal"
        "NUMERIC"             || "java.math.BigDecimal"
        "NUMBER"              || "java.math.BigDecimal"
        "VARCHAR"             || "java.lang.String"
        "VARCHAR2"            || "java.lang.String"
        "CHAR"                || "java.lang.String"
        "NVARCHAR"            || "java.lang.String"
        "TEXT"                || "java.lang.String"
        "CLOB"                || "java.lang.String"
        "LONGVARCHAR"         || "java.lang.String"
        "CHARACTER VARYING"   || "java.lang.String"
        // H2 2.x CLOB — reported as CHARACTER LARGE OBJECT
        "CHARACTER LARGE OBJECT" || "java.lang.String"
        "CHARACTER LARGE"     || "java.lang.String"
        "DATE"                || "java.sql.Date"
        "TIME"                || "java.sql.Time"
        "TIMESTAMP"           || "java.sql.Timestamp"
        "BOOLEAN"             || "java.lang.Boolean"
        "BOOL"                || "java.lang.Boolean"
        "BIT"                 || "java.lang.Boolean"
        "BLOB"                || "byte[]"
        "BINARY"              || "byte[]"
        "VARBINARY"           || "byte[]"
        "LONGVARBINARY"       || "byte[]"
        "JSONB"               || "java.lang.Object"
        null                  || "java.lang.Object"
    }

    def "toJavaType handles vendor variants with prefix matching"() {
        expect:
        // TIMESTAMP(6) starts with TIMESTAMP
        JdbcTypeMapper.toJavaType("TIMESTAMP(6)") == "java.sql.Timestamp"
        // TIMESTAMP WITH TIME ZONE starts with TIMESTAMP
        JdbcTypeMapper.toJavaType("TIMESTAMP WITH TIME ZONE") == "java.sql.Timestamp"
        // TINYINT UNSIGNED starts with TINYINT
        JdbcTypeMapper.toJavaType("TINYINT UNSIGNED") == "java.lang.Integer"
    }

    def "toJavaType is case-insensitive"() {
        expect:
        JdbcTypeMapper.toJavaType("integer") == "java.lang.Integer"
        JdbcTypeMapper.toJavaType("varchar") == "java.lang.String"
        JdbcTypeMapper.toJavaType("timestamp") == "java.sql.Timestamp"
    }
}