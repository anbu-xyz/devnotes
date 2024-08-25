package uk.anbu.devnotes.controller;

import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.anbu.devnotes.service.ConfigService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ConfigController {

    private final TemplateEngine templateEngine;
    private final ConfigService configService;

    @GetMapping("/config")
    public ResponseEntity<String> configPage() {
        var model = new HashMap<String, Object>();
        model.put("markdownDirectory", configService.getDocsDirectory());
        model.put("sshKeyFile", configService.getSshKey().orElse("Not set"));
        model.put("dataSources", configService.getDataSources());
        model.put("sqlMaxRows", configService.getSqlMaxRows());
        TemplateOutput output = new StringOutput();
        templateEngine.render("config.jte", model, output);
        return ResponseEntity.status(HttpStatus.OK)
                .body(output.toString());
    }

    @PostMapping("/config")
    public ResponseEntity<String> updateConfig(@RequestParam Map<String, String> params) {
        configService.updateDataSources(params);
        if (params.containsKey("sqlMaxRows")) {
            configService.setSqlMaxRows(Integer.parseInt(params.get("sqlMaxRows")));
        }
        configService.saveAndReloadConfig();

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "/config")
                .build();
    }
}