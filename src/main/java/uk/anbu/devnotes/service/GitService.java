package uk.anbu.devnotes.service;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.ssh.jsch.JschConfigSessionFactory;
import org.eclipse.jgit.transport.ssh.jsch.OpenSshConfig;
import org.eclipse.jgit.util.FS;
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

            git.add().addFilepattern(".").call();
            // Add all changes, including new files, modifications, and deletions
            git.add().setUpdate(true).addFilepattern(".").call();
            Set<String> changes = git.status().call().getUncommittedChanges();
            String changesString = String.join(", ", changes);

            git.commit().setMessage("Auto-commit changed files: " + changesString).call();
            log.info("Auto-commit changed files: " + changesString);

            if (hasRemote(git)) {
                pushChanges(git);
            }

            return true;
        }
    }

    private boolean isGitRepository(File directory) {
        try {
            try(Repository repository = new FileRepositoryBuilder()
                    .setGitDir(new File(directory, ".git"))
                    .readEnvironment()
                    .findGitDir()
                    .build()) {
                return repository.getObjectDatabase().exists();
            }
        } catch (IOException e) {
            log.error("Error checking if directory is a Git repository", e);
            return false;
        }
    }

    private boolean hasRemote(Git git) {
        Config config = git.getRepository().getConfig();
        Set<String> remotes = config.getSubsections("remote");
        return !remotes.isEmpty();
    }

    private void pushChanges(Git git) throws GitAPIException {
        final SshSessionFactory sshSessionFactory = new JschConfigSessionFactory() {
            @Override
            protected void configure(OpenSshConfig.Host host, Session session) {
            }

            @Override
            protected JSch createDefaultJSch(FS fs) throws JSchException {
                JSch defaultJSch = super.createDefaultJSch(fs);
                var sshKey = configService.getSshKey();
                if (sshKey.isPresent()) {
                    defaultJSch.addIdentity(sshKey.get());
                } else {
                    var homeDir = System.getProperty("user.home");
                    var idRsa = new File(homeDir, ".ssh/id_rsa.jsch");
                    if (!idRsa.exists()) {
                        idRsa = new File(homeDir, ".ssh/id_rsa");
                    }
                    log.info("No ssh key set. Trying default key {}", idRsa.getAbsolutePath());
                    defaultJSch.addIdentity(idRsa.getAbsolutePath());
                }
                return defaultJSch;
            }
        };

        log.debug("Pushing changes to remote repository");
        PushCommand pushCommand = git.push();
        pushCommand.setTransportConfigCallback(transport -> {
            SshTransport sshTransport = (SshTransport) transport;
            sshTransport.setSshSessionFactory(sshSessionFactory);
        });
        pushCommand.call();
        log.debug("Changes pushed successfully");
    }
}