package uk.anbu.devtools.service;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
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
        File gitDir = new File(configService.getDocsDirectory());

        if (!isGitRepository(gitDir)) {
            log.warn("The docs directory is not a Git repository. Skipping commit and push.");
            return false;
        }

        try (Git git = Git.open(gitDir)) {
            Status status = git.status().call();

            if (status.isClean()) {
                log.info("No changes detected in Git repository");
                return false;
            }

            log.debug("Changes detected, adding all changes to Git repository");

            // Add all changes, including new files, modifications, and deletions
            git.add().setUpdate(true).addFilepattern(".").call();

            // Commit the changes
            git.commit().setMessage("Auto-commit: " + java.time.LocalDateTime.now()).call();

            if (hasRemote(git)) {
                pushChanges(git);
            }

            return true;
        }
    }

    private boolean isGitRepository(File directory) {
        try {
            Repository repository = new FileRepositoryBuilder()
                    .setGitDir(new File(directory, ".git"))
                    .readEnvironment()
                    .findGitDir()
                    .build();
            return repository.getObjectDatabase().exists();
        } catch (IOException e) {
            log.error("Error checking if directory is a Git repository", e);
            return false;
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