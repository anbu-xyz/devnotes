package uk.anbu.devtools.controller;

import java.util.Map;

@lombok.Data
public class SqlExecutionRequest {
    private String datasourceName;
    private String sql;
    private String markdownFileName;
    private Map<String, String> parameterValues;
}
