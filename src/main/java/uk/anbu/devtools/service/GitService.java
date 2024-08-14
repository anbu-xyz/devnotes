package uk.anbu.devtools.service;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

@Service
@Slf4j
public class GitService {

    private final ConfigService configService;

    @Autowired
    public GitService(ConfigService configService) {
        this.configService = configService;
    }

    public void commitChanges() throws IOException, GitAPIException {
        File gitDir = new File(configService.getMarkdownDirectory());
        try (Git git = Git.open(gitDir)) {
            log.info("Committing changes to Git repository");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("Auto-commit: " + java.time.LocalDateTime.now()).call();
        }
    }
}
