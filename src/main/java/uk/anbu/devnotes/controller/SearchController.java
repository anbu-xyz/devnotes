package uk.anbu.devnotes.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devnotes.module.SearchExecutor;
import uk.anbu.devnotes.service.ConfigService;

import java.util.HashMap;

@Slf4j
@RequiredArgsConstructor
@RestController
public class SearchController {

    private final ConfigService configService;
    private final TemplateEngine templateEngine;
    private final ObjectMapper objectMapper;

    @GetMapping("/search")
    public ResponseEntity<String> search(@RequestParam("q") String searchParam,
          @RequestParam(value = "caseSensitive", required = false, defaultValue = "false") boolean caseSensitive,
          @RequestParam(value="renderHtmlResults", required = false, defaultValue = "false") boolean renderHtmlResults) {
        try {
            searchParam = searchParam.trim();
            var result = new SearchExecutor(configService.getDocsDirectory())
                    .search(searchParam, caseSensitive);
            if (result.isEmpty()) {
                return ResponseEntity.notFound().build();
            } else {
                if (renderHtmlResults) {
                    TemplateOutput output = new StringOutput();
                    var params = new HashMap<String, Object>();
                    params.put("results", result);
                    params.put("searchTerm", searchParam);
                    templateEngine.render("tools/search-results.jte", params, output);
                    return ResponseEntity.ok()
                            .contentType(org.springframework.http.MediaType.TEXT_HTML)
                            .body(output.toString());
                } else {
                    var jsonOutput = objectMapper.writeValueAsString(result);
                    return ResponseEntity.ok()
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .body(jsonOutput);
                }
            }
        } catch (Exception e) {
            log.error("Error searching", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error searching for " + searchParam);
        }
    }

}
