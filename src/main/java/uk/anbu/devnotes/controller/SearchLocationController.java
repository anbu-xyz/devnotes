package uk.anbu.devnotes.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devnotes.module.SearchLocationExecutor;
import uk.anbu.devnotes.service.ConfigService;
import uk.anbu.devnotes.types.SearchResult;
import uk.anbu.devnotes.types.SearchResultList;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestController
public class SearchLocationController {

    private final ConfigService configService;
    private final TemplateEngine templateEngine;
    private final ObjectMapper objectMapper;

    @GetMapping("/searchLocation")
    public ResponseEntity<String> searchLocation(
            @RequestParam("q") String searchParam,
            @RequestParam(value = "path", required = false) String searchPath,
            @RequestParam(value= "json", required = false, defaultValue = "false") boolean renderJsonResults) {
        try {
            var docsDir = Path.of(configService.getDocsDirectory());

            var result = new SearchLocationExecutor(docsDir, searchParam)
                    .getResult();

            if (result.results().isEmpty()) {
                return ResponseEntity.notFound().build();
            } else {
                if (renderJsonResults) {
                    return renderJsonResults(result);
                } else {
                    return renderHtmlResults(searchParam, searchPath, result);
                }
            }
        } catch (Exception e) {
            log.error("Error searching", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error searching for " + searchParam);
        }
    }

    private ResponseEntity<String> renderJsonResults(SearchResultList result) throws JsonProcessingException {
        var jsonOutput = objectMapper.writeValueAsString(result);
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(jsonOutput);
    }

    private ResponseEntity<String> renderHtmlResults(String searchParam, String searchPath, SearchResultList result) {
        return constructResponse(searchParam, searchPath, result, templateEngine);
    }

    static ResponseEntity<String> constructResponse(String searchParam, String searchPath,
                                                    SearchResultList result,
                                                    TemplateEngine templateEngine) {
        TemplateOutput output = new StringOutput();
        var params = new HashMap<String, Object>();
        params.put("extensions", result.extensions());
        params.put("results", result);
        params.put("searchTerm", searchParam);
        templateEngine.render("tools/search-results.jte", params, output);
        if (result.results().size() == 1) {
            return ResponseEntity
                    .status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "/markdown?filename=" + result.results().get(0).fileName())
                    .build();
        } else {
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.TEXT_HTML)
                    .body(output.toString());
        }
    }

}
