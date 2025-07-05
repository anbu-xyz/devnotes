package uk.anbu.devnotes.scheduled;

import groovy.lang.GroovyShell;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import uk.anbu.devnotes.util.FileUtil;

import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
public class GroovyScriptJob implements Job {
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            JobDataMap dataMap = context.getJobDetail().getJobDataMap();
            String scriptFile = FileUtil.cleanDirectoryName(dataMap.getString("scriptFile"));
            String docsRoot = dataMap.getString("docsRoot");

            Path docsDirectory = Path.of(docsRoot);
            Path fullPath = docsDirectory.resolve(scriptFile);

            if (!Files.exists(fullPath)) {
                throw new JobExecutionException("Script file not found: " + fullPath);
            }

            String scriptContent = Files.readString(fullPath);
            log.info("Executing script: {}", fullPath);

            GroovyShell shell = new GroovyShell();
            shell.evaluate(scriptContent);

            log.info("Script execution completed: {}", fullPath);
        } catch (Exception e) {
            log.error("Failed to execute script", e);
            throw new JobExecutionException(e);
        }
    }
}