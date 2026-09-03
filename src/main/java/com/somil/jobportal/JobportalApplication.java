package com.somil.jobportal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class JobportalApplication {

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(JobportalApplication.class);
		application.addInitializers(new ProfileSchemaInitializer());
		application.run(args);
	}
}
