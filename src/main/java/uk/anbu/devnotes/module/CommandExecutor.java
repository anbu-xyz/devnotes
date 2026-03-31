package uk.anbu.devnotes.module;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import uk.anbu.devnotes.controller.SearchController;
import uk.anbu.devnotes.controller.SearchLocationController;
import uk.anbu.devnotes.controller.TodoController;
import uk.anbu.devnotes.types.CommandInterface;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommandExecutor {

    private final TodoController todoController;

    private final SearchController searchController;

    private final SearchLocationController searchLocationController;

    public ResponseEntity<String> executeCommand(CommandInterface command) {
        if ("todo".equalsIgnoreCase(command.command())) {
            if (command.restOfCommand() == null || command.restOfCommand().isEmpty()) {
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, "/markdown?filename=/Todo.md")
                        .build();
            }
            return todoController.addFleetingItem(command.restOfCommand());
        } else if ("search".equalsIgnoreCase(command.command())) {
            if (command.restOfCommand() == null || command.restOfCommand().isEmpty()) {
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, "/search")
                        .build();
            }
            return searchController.search(command.restOfCommand(), "", false, false, true);
        } else if ("location".equalsIgnoreCase(command.command())) {
            if (command.restOfCommand() == null || command.restOfCommand().isEmpty()) {
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, "/searchLocation")
                        .build();
            }
            return searchLocationController.searchLocation(command.restOfCommand(), "", true);
        }
        return ResponseEntity.notFound().build();
    }
}
