package uk.anbu.devtools.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devtools.module.SqlExecutor;
import uk.anbu.devtools.service.ConfigService;

@RestController
@RequiredArgsConstructor
@Slf4j
public class SqlExecutionController {

    private final ConfigService configService;
    private final SqlExecutor sqlExecutor;

    @PostMapping(value = "/reExecuteSql", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> reExecuteSql(@RequestBody SqlExecutionRequest request) {
        String dataSourceName = request.getDatasourceName();
        ConfigService.DataSourceConfig dataSourceConfig = configService.getDataSourceConfig(dataSourceName);

        if (dataSourceConfig == null) {
            log.error("Invalid datasource name: {}", dataSourceName);
            return ResponseEntity.badRequest().body("Invalid datasource name:" + dataSourceName);
        }

        var outputPath = sqlExecutor.executeSqlAndSaveOutput(dataSourceConfig, request.getSql(),
                request.getParameterValues(), request.getMarkdownFileName());

        String htmlTable = sqlExecutor.convertToHtmlTable(request.getSql(), outputPath,
                request.getParameterValues(), request.getDatasourceName(), request.getMarkdownFileName());

        return ResponseEntity.ok(htmlTable);
    }

    @GetMapping("/downloadExcel")
    public ResponseEntity<Resource> downloadExcel(@RequestParam String fileName) {
        // Implement Excel download logic here
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=" + fileName).body(new ClassPathResource("excel.xlsx"));
    }
}
