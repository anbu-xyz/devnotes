package uk.anbu.devnotes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.stereotype.Service;
import uk.anbu.devnotes.scheduled.GroovyScriptJob;
import uk.anbu.devnotes.types.ScriptSchedule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ScriptSchedulerService {
    private static final String CRON_FILENAME = "cron.yaml";
    private static final String GIT_IGNORE_FILENAME = ".gitignore";
    private final ConfigService configService;
    private final Scheduler scheduler;

    public ScriptSchedulerService(ConfigService configService) throws SchedulerException {
        this.configService = configService;
        SchedulerFactory schedulerFactory = new StdSchedulerFactory();
        this.scheduler = schedulerFactory.getScheduler();
        this.scheduler.start();
        loadAndScheduleAllJobs();
    }

    private void loadAndScheduleAllJobs() {
        try {
            List<ScriptSchedule> schedules = loadSchedules();
            for (ScriptSchedule schedule : schedules) {
                scheduleJob(schedule);
            }
        } catch (IOException | SchedulerException e) {
            log.error("Failed to load and schedule jobs", e);
        }
    }

    public List<ScriptSchedule> loadSchedules() throws IOException {
        Path cronPath = getCronFilePath(configService.getDocsDirectory());
        if (!Files.exists(cronPath)) {
            saveSchedules(new ArrayList<>());
            return new ArrayList<>();
        }

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());
        return mapper.readValue(cronPath.toFile(),
                mapper.getTypeFactory().constructCollectionType(List.class, ScriptSchedule.class));
    }

    public void saveSchedules(List<ScriptSchedule> schedules) throws IOException {
        Path cronPath = getCronFilePath(configService.getDocsDirectory());
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());
        mapper.writeValue(cronPath.toFile(), schedules);
    }

    private static Path getCronFilePath(String docsDirectory) throws IOException {
        Path docsDirectoryPath = Path.of(docsDirectory);
        Path configDirectory = docsDirectoryPath.resolve("config");
        Path cronPath = configDirectory.resolve(CRON_FILENAME);
        Path dotGitPath = configDirectory.resolve(GIT_IGNORE_FILENAME);

        Files.createDirectories(configDirectory);
        createGitIgnoreFile(dotGitPath);
        return cronPath;
    }

    private static void createGitIgnoreFile(Path dotGitPath) throws IOException {
        if (Files.exists(dotGitPath)) {
            String content = Files.readString(dotGitPath, StandardCharsets.UTF_8);
            if (!content.contains(CRON_FILENAME)) {
                Files.writeString(dotGitPath, content + "\n" + CRON_FILENAME, StandardCharsets.UTF_8);
            }
        } else {
            Files.writeString(dotGitPath, CRON_FILENAME + "\n", StandardCharsets.UTF_8);
        }
    }

    public void scheduleJob(ScriptSchedule schedule) throws SchedulerException {
        JobDetail job = JobBuilder.newJob(GroovyScriptJob.class)
                .withIdentity(schedule.id())
                .usingJobData("scriptFile", schedule.scriptFile())
                .usingJobData("docsRoot", configService.getDocsDirectory())
                .build();

        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(schedule.id())
                .withSchedule(CronScheduleBuilder.cronSchedule(schedule.cronExpression()))
                .build();

        scheduler.scheduleJob(job, trigger);
    }

    public boolean deleteJob(String id) throws SchedulerException {
        return scheduler.deleteJob(new JobKey(id));
    }

}