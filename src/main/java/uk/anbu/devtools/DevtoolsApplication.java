package uk.anbu.devtools;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DevtoolsApplication {

	public static void main(String[] args) {
		SpringApplication.run(DevtoolsApplication.class, args);
	}

}
