package uk.anbu.devnotes.util;

import java.util.Set;

/**
 * Pure-static utility that maps JDBC metadata values to the field names and type
 * strings used by {@code database-metadata} blocks.
 */
public final class JdbcTypeMapper {

    /** Types whose size/precision is fixed and should not be included in the output string. */
    private static final Set<String> NO_SIZE_TYPES = Set.of(
            "INTEGER", "INT", "SMALLINT", "TINYINT", "MEDIUMINT",
            "BIGINT", "BOOLEAN", "BOOL", "BIT"
    );

    private JdbcTypeMapper() {}

    /**
     * Returns the {@code database-metadata} YAML field name that matches the live DB product:
     * {@code "h2-type"} for H2, {@code "oracle-type"} for Oracle, {@code "db-type"} otherwise.
     */
    public static String resolveTypeFieldName(String dbProductName) {
        if (dbProductName == null) return "db-type";
        return switch (dbProductName.toLowerCase()) {
            case "h2"     -> "h2-type";
            case "oracle" -> "oracle-type";
            default       -> "db-type";
        };
    }

    /**
     * Formats a human-readable type string from JDBC column metadata.
     * <ul>
     *   <li>Integer / boolean types - returned as-is (no size suffix).</li>
     *   <li>Types with non-zero decimal digits - {@code TYPE(size,digits)}.</li>
     *   <li>All other types with a positive size - {@code TYPE(size)}.</li>
     * </ul>
     */
    public static String formatType(String dbProductName, String typeName, int size, int digits) {
        if (typeName == null) return "";
        String upper = typeName.toUpperCase();

        // Integer and boolean families: no size suffix
        if (isNoSizeType(upper)) {
            return typeName;
        }

        // Decimal precision: TYPE(size,digits)
        if (digits > 0) {
            return typeName + "(" + size + "," + digits + ")";
        }

        // No meaningful size
        if (size <= 0) {
            return typeName;
        }

        return typeName + "(" + size + ")";
    }

    private static boolean isNoSizeType(String upper) {
        if (NO_SIZE_TYPES.contains(upper)) return true;
        // Handle vendor variants and unsigned suffixes, e.g. "TINYINT UNSIGNED"
        return upper.startsWith("BIGINT")
                || upper.startsWith("INTEGER")
                || upper.startsWith("SMALLINT")
                || upper.startsWith("TINYINT")
                || upper.startsWith("MEDIUMINT")
                || upper.startsWith("BOOLEAN")
                || upper.startsWith("BOOL")
                || upper.startsWith("BIT");
    }

    /**
     * Maps a JDBC {@code TYPE_NAME} to the canonical Java class name.
     * Uses case-insensitive prefix matching so vendor variants
     * (e.g. {@code TIMESTAMP(6)}, {@code TIMESTAMP WITH TIME ZONE}) still resolve correctly.
     */
    public static String toJavaType(String typeName) {
        if (typeName == null) return "java.lang.Object";
        String upper = typeName.toUpperCase();

        if (upper.startsWith("BIGINT"))                          return "java.lang.Long";
        if (upper.startsWith("INTEGER") || upper.startsWith("INT")
                || upper.startsWith("SMALLINT") || upper.startsWith("TINYINT")
                || upper.startsWith("MEDIUMINT"))                return "java.lang.Integer";
        if (upper.startsWith("FLOAT") || upper.startsWith("REAL")) return "java.lang.Float";
        if (upper.startsWith("DOUBLE"))                          return "java.lang.Double";
        if (upper.startsWith("NUMERIC") || upper.startsWith("DECIMAL")
                || upper.startsWith("NUMBER"))                   return "java.math.BigDecimal";
        if (upper.startsWith("CHARACTER VARYING") || upper.startsWith("CHARACTER LARGE")
                || upper.startsWith("VARCHAR") || upper.startsWith("CHAR")
                || upper.startsWith("NVARCHAR") || upper.startsWith("NCHAR")
                || upper.startsWith("TEXT") || upper.startsWith("CLOB")
                || upper.startsWith("LONGVARCHAR") || upper.startsWith("NCLOB"))
                                                                 return "java.lang.String";
        if (upper.startsWith("TIMESTAMP"))                       return "java.sql.Timestamp";
        if (upper.startsWith("DATE"))                            return "java.sql.Date";
        if (upper.startsWith("TIME"))                            return "java.sql.Time";
        if (upper.startsWith("BOOLEAN") || upper.startsWith("BOOL")
                || upper.startsWith("BIT"))                      return "java.lang.Boolean";
        if (upper.startsWith("BLOB") || upper.startsWith("BINARY")
                || upper.startsWith("VARBINARY") || upper.startsWith("LONGVARBINARY"))
                                                                 return "byte[]";

        return "java.lang.Object";
    }
}