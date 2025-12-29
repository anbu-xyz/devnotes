package uk.anbu.devnotes.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.jte.TemplateEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devnotes.module.SearchExecutor;
import uk.anbu.devnotes.service.ConfigService;
import uk.anbu.devnotes.types.SearchResultList;

import static uk.anbu.devnotes.controller.SearchLocationController.constructResponse;

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
          @RequestParam(value= "json", required = false, defaultValue = "false") boolean renderJsonResults) {
        try {
            searchParam = searchParam.trim();
            var result = new SearchExecutor(configService.getDocsDirectory())
                    .search(searchParam, caseSensitive);
            if (result.results().isEmpty()) {
                return ResponseEntity.notFound().build();
            } else {
                if (renderJsonResults) {
                    return renderJsonResults(result);
                } else {
                    return renderHtmlResults(searchParam, result);
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

    private ResponseEntity<String> renderHtmlResults(String searchParam, SearchResultList result) {
        return constructResponse(searchParam, result, templateEngine);
    }

}
