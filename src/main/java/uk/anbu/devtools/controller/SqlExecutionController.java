package uk.anbu.devtools.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devtools.module.MarkdownRenderer;
import uk.anbu.devtools.service.ConfigService;

@RestController
@RequiredArgsConstructor
public class SqlExecutionController {

    private final ConfigService configService;
    private final MarkdownRenderer markdownRenderer;

    @PostMapping("/reExecuteSql")
    public ResponseEntity<String> reExecuteSql(@RequestBody SqlExecutionRequest request) {
        // Re-execute SQL and return updated HTML table
        // This method should mimic the SQL execution logic in MarkdownRenderer
        // but return only the HTML table as a string
        // ...
        // return ResponseEntity.ok(markdownRenderer.convertMarkdown(sql, fileName));
        return ResponseEntity.ok("Not implemented yet");
    }

    @GetMapping("/downloadExcel")
    public ResponseEntity<Resource> downloadExcel(@RequestParam String fileName) {
        // Read the JSON file and convert it to Excel
        // Return the Excel file as a downloadable resource
        // ...
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=" + fileName).body(new ClassPathResource("excel.xlsx"));
    }
}
