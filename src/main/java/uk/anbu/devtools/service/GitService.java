package uk.anbu.devtools.service;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Set;

@Service
@Slf4j
public class GitService {

    private final ConfigService configService;

    @Autowired
    public GitService(ConfigService configService) {
        this.configService = configService;
    }

    public boolean commitAndPushChanges() throws IOException, GitAPIException {
        File gitDir = new File(configService.getMarkdownDirectory());
        try (Git git = Git.open(gitDir)) {
            Status status = git.status().call();

            if (status.isClean()) {
                return false;
            }

            log.debug("Changes detected, committing to Git repository");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("Auto-commit: " + java.time.LocalDateTime.now()).call();

            if (hasRemote(git)) {
                pushChanges(git);
            }

            return true;
        }
    }

    private boolean hasRemote(Git git) throws GitAPIException {
        Config config = git.getRepository().getConfig();
        Set<String> remotes = config.getSubsections("remote");
        return !remotes.isEmpty();
    }

    private void pushChanges(Git git) throws GitAPIException {
        log.debug("Pushing changes to remote repository");
        PushCommand pushCommand = git.push();
        // pushCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider("username", "password")); // Replace with actual credentials or use SSH
        pushCommand.call();
        log.debug("Changes pushed successfully");
    }
}