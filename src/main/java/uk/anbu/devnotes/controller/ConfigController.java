package uk.anbu.devnotes.controller;

import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.anbu.devnotes.service.ConfigServiceImpl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class ConfigController {

    private final TemplateEngine templateEngine;
    private final ConfigServiceImpl configService;

    @GetMapping("/config")
    public ResponseEntity<String> configPage() {
        var model = new HashMap<String, Object>();
        model.put("markdownDirectory", configService.getDocsDirectory());
        model.put("sshKeyFile", configService.getSshKey().orElse("Not set"));
        model.put("dataSources", configService.getDataSources());
        model.put("sqlMaxRows", configService.getSqlMaxRows());
        model.put("chromeDriverLocation",
            configService.getChromeDriverLocation().orElse("Not set"));
        TemplateOutput output = new StringOutput();
        templateEngine.render("tools/config.jte", model, output);
        return ResponseEntity.status(HttpStatus.OK)
            .body(output.toString());
    }

    @PostMapping("/config")
    public ResponseEntity<String> updateConfig(
        @RequestParam Map<String, String> params,
        @RequestParam(value = "delete_datasource", required = false) List<String> toDelete) {
        if (toDelete != null && !toDelete.isEmpty()) {
            params = filterParamsForDeletedDatasources(params, toDelete);
        }
        configService.updateDataSources(params);
        if (params.containsKey("sqlMaxRows")) {
            configService.setSqlMaxRows(Integer.parseInt(params.get("sqlMaxRows")));
        }
        configService.setChromeDriverLocation(params.get("chromeDriverLocation"));
        configService.saveAndReloadConfig();

        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, "/config")
            .build();
    }

    private Map<String, String> filterParamsForDeletedDatasources(Map<String, String> params,
                                                                  List<String> toDelete) {
        Set<String> toDeleteSet = new HashSet<>(toDelete);
        // Remove from the live map so updateDataSources won't find them to copy
        toDeleteSet.forEach(name -> configService.getDataSources().remove(name));
        // Strip any datasources[name].* params for deleted names so they can't be recreated
        return params.entrySet().stream()
            .filter(e -> isDatasourceParameterRetained(e, toDeleteSet))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static boolean isDatasourceParameterRetained(Map.Entry<String, String> e,
                                                         Set<String> toDeleteSet) {
        String[] parts = e.getKey().split("\\.", 2);
        if (parts.length == 2
            && parts[0].startsWith("datasources[")
            && parts[0].endsWith("]")) {
            String name = parts[0].substring(12, parts[0].length() - 1);
            return !toDeleteSet.contains(name);
        }
        return true;
    }
}