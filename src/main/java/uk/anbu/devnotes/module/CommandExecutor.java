package uk.anbu.devnotes.module;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import uk.anbu.devnotes.controller.TodoController;

@Component
@RequiredArgsConstructor
public class CommandExecutor {

    private final TodoController todoController;

    public ResponseEntity<String> executeCommand(String command, String parameter) {
        if ("todo".equalsIgnoreCase(command)) {
            if (parameter == null || parameter.isEmpty()) {
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, "/markdown?filename=/Todo.md")
                        .build();
            }
            return todoController.addFleetingItem(parameter);
        }
        return ResponseEntity.notFound().build();
    }
}
