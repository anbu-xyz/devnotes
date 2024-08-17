package uk.anbu.devtools.controller;

import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.anbu.devtools.service.ConfigService;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ConfigController {

    private final TemplateEngine templateEngine;
    private final ConfigService configService;

    @GetMapping("/config")
    public ResponseEntity<String> configPage() {
        Map<String, Object> model = Map.of(
                "markdownDirectory", configService.getDocsDirectory(),
                "sshKeyFile", configService.getSshKey().orElse("Not set"),
                "dataSources", configService.getDataSources()
        );
        TemplateOutput output = new StringOutput();
        templateEngine.render("config.jte", model, output);
        return ResponseEntity.status(HttpStatus.OK)
                .body(output.toString());
    }

    @PostMapping("/config")
    public ResponseEntity<String> updateConfig(@RequestParam Map<String, String> datasources) {
        configService.updateDataSources(datasources);
        configService.saveAndReloadConfig();

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "/config")
                .build();
    }
}