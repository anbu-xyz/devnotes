package uk.anbu.devnotes.controller;

import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ToolsController {

    private final TemplateEngine templateEngine;

    @GetMapping("/tools")
    public ResponseEntity<String> pomodoroPage() {
        var model = new HashMap<String, Object>();
        TemplateOutput output = new StringOutput();
        templateEngine.render("tools.jte", model, output);
        return ResponseEntity.ok(output.toString());
    }

}