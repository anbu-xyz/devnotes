package uk.anbu.devtools.controller;

import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import uk.anbu.devtools.service.ConfigService;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ConfigController {

    private final TemplateEngine templateEngine;
    private final ConfigService configService;

    @GetMapping("/config")
    public String configPage() {
        Map<String, Object> model = Map.of(
                "markdownDirectory", configService.getMarkdownDirectory()
        );
        TemplateOutput output = new StringOutput();
        templateEngine.render("config.jte", model, output);
        return output.toString();
    }

    @PostMapping("/config")
    public String updateConfig(@RequestParam String markdownDirectory) {
        configService.setMarkdownDirectory(markdownDirectory);
        return "redirect:/config";
    }
}