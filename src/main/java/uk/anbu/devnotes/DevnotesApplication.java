package uk.anbu.devnotes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DevnotesApplication {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.setProperty("spring.profiles.active", "prod");
        } else {
            System.setProperty("spring.profiles.active", args[0]);
        }
        SpringApplication.run(DevnotesApplication.class, args);
    }

}
