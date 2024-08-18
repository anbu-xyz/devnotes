package uk.anbu.devnotes.scheduled;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uk.anbu.devnotes.service.GitService;

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
            boolean committed = gitService.commitAndPushChanges();
            if (committed) {
                log.info("Committed changes");
            } else {
                log.info("No changes to commit");
            }
        } catch (IOException | GitAPIException e) {
            log.error("Error during Git operation", e);
        }
    }
}
