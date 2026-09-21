package com.somil.jobportal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class JobportalApplication {

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(JobportalApplication.class);
		// Validate configuration first so a missing secret fails here with a clear message,
		// before anything tries to open a database connection.
		application.addInitializers(new RequiredConfigurationValidator(), new ProfileSchemaInitializer());
		application.run(args);
	}
}
