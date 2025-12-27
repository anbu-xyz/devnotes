package uk.anbu.devnotes.controller;

import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devnotes.module.CommandExecutor;
import uk.anbu.devnotes.types.Command;

import java.util.HashMap;

@RestController
@RequiredArgsConstructor
@Slf4j
public class AwesomeBarController {
    private final CommandExecutor commandExecutor;

    private final TemplateEngine templateEngine;

    @GetMapping("/awesome-bar")
    public ResponseEntity<String> awesomeBar() {
        var model = new HashMap<String, Object>();
        TemplateOutput output = new StringOutput();
        templateEngine.render("tools/awesome-bar.jte", model, output);
        return ResponseEntity.status(HttpStatus.OK)
                .body(output.toString());
    }

    @GetMapping("/awesome-bar/command")
    public ResponseEntity<String> postAwesomeBar(@RequestParam("q") String command) {
        log.info("Command: {}", command);
        String[] words = command.split("\\s+");
        String firstWord = words[0];
        String restOfCommand = command.substring(firstWord.length()).trim();
        return commandExecutor.executeCommand(new Command(firstWord, restOfCommand));
    }

    @PostMapping("/awesome-bar/todo")
    public ResponseEntity<String> addTodo(@RequestParam("todoText") String todoString) {
        return commandExecutor.executeCommand(new Command("todo", todoString));
    }

    @PostMapping("/awesome-bar/search")
    public ResponseEntity<String> search(@RequestParam("searchText") String searchText) {
        return commandExecutor.executeCommand(new Command("search", searchText));
    }

    @PostMapping("/awesome-bar/searchLocation")
    public ResponseEntity<String> searchLocation(@RequestParam("searchLocationText") String searchLocationText) {
        return commandExecutor.executeCommand(new Command("searchLocationText", searchLocationText));
    }
}