package uk.anbu.devtools;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DevtoolsApplication {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.setProperty("spring.profiles.active", "prod");
        } else {
            System.setProperty("spring.profiles.active", args[0]);
        }
        SpringApplication.run(DevtoolsApplication.class, args);
    }

}
