package uk.anbu.devnotes.module;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import uk.anbu.devnotes.controller.TodoController;

@Component
@RequiredArgsConstructor
public class CommandExecutor {

    private final TodoController todoController;

    public ResponseEntity<String> executeCommand(String command) {
        String[] words = command.split("\\s+");
        String firstWord = words[0];
        String restOfCommand = command.substring(firstWord.length()).trim();

        if ("todo".equalsIgnoreCase(firstWord)) {
            return todoController.addFleetingItem(restOfCommand);
        }
        return ResponseEntity.notFound().build();
    }
}
