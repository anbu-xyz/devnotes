package uk.anbu.devnotes.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devnotes.markdown.TodoModifier;
import uk.anbu.devnotes.service.ConfigService;

import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@RequiredArgsConstructor
@RestController
public class TodoController {

    private final ConfigService configService;

    private static final String filename = "Todo.md";

    @PostMapping("/addFleetingItem")
    public ResponseEntity<String> addFleetingItem(@RequestBody String newTodoItem) {
        try {
            Path markdownRoot = Paths.get(configService.getDocsDirectory());
            Path todoFilePath = markdownRoot.resolve(filename);
            TodoModifier.addFleetingItem(todoFilePath, newTodoItem);
            return ResponseEntity.status(HttpStatus.OK).body("Added new fleeting item");
        } catch (Exception e) {
            log.error("Error adding fleeting item", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error adding fleeting item");
        }
    }

}
