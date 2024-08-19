package uk.anbu.devnotes;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@Slf4j
public class DevnotesApplication {

    public static void main(String[] args) {
        // Oracle JDBC driver requires this property to be set to return timestamps as java.sql.Timestamp
        // instead of oracle.sql.TIMESTAMP
        System.setProperty("oracle.jdbc.J2EE13Compliant", "true");

        String profile;
        if (args.length == 0) {
            profile =  "prod";
        } else {
            profile = args[0];
        }
        log.info("Running with profile: {}", profile);
        SpringApplication.run(DevnotesApplication.class, args);
    }

}
