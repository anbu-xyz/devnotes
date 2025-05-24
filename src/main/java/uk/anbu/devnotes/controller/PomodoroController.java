package uk.anbu.devnotes.controller;

import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devnotes.service.ConfigService;

import java.util.HashMap;

@RestController
@RequiredArgsConstructor
public class PomodoroController {

    private final TemplateEngine templateEngine;
    private final ConfigService configService;

    @GetMapping("/pomodoro")
    public ResponseEntity<String> configPage() {
        var model = new HashMap<String, Object>();
        model.put("markdownDirectory", configService.getDocsDirectory());
        TemplateOutput output = new StringOutput();
        templateEngine.render("pomodoro.jte", model, output);
        return ResponseEntity.status(HttpStatus.OK)
                .body(output.toString());
    }
}