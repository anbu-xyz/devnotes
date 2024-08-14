package uk.anbu.devtools.scheduled;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uk.anbu.devtools.service.GitService;

import java.io.IOException;

@Component
@Slf4j
public class GitScheduledTasks {

    private final GitService gitService;

    @Autowired
    public GitScheduledTasks(GitService gitService) {
        this.gitService = gitService;
    }

    @Scheduled(fixedRate = 300000)
    public void scheduleGitCommit() {
        try {
            gitService.commitChanges();
            log.info("Git commit completed successfully");
        } catch (IOException | GitAPIException e) {
            log.error("Error while commiting changes", e);
        }
    }
}
