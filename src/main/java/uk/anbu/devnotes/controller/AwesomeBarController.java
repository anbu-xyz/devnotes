package uk.anbu.devnotes.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devnotes.module.CommandExecutor;

@RestController
@RequiredArgsConstructor
@Slf4j
public class AwesomeBarController {

    private final CommandExecutor commandExecutor;

    @GetMapping("/awesome-bar/command")
    public ResponseEntity<String> postAwesomeBar(@RequestParam("q") String command) {
        log.info("Command: {}", command);
        return commandExecutor.executeCommand(command);
    }

}