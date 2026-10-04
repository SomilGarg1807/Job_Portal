package com.somil.jobportal.eval;

import java.nio.file.Path;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Profile;

/** An isolated Spring profile: no production component scan or datasource auto configuration. */
@SpringBootConfiguration
@Profile("eval")
public class EvalApplication {
    public static void main(String[] args) {
        try {
            System.setProperty("spring.devtools.restart.enabled", "false");
            if (java.util.Arrays.stream(args).noneMatch("--spring.profiles.active=eval"::equals))
                throw new IllegalArgumentException("Start the harness with --spring.profiles.active=eval.");
            EvalConfig config = EvalConfig.load(Path.of(".env.eval"));
            try (var context = new SpringApplicationBuilder(EvalApplication.class).web(WebApplicationType.NONE)
                    .profiles("eval").properties("spring.devtools.restart.enabled=false")
                    .logStartupInfo(false).run(args)) {
                EvalRunner.run(config, Path.of("eval"));
            }
        } catch (Exception ex) {
            // JDBC exceptions can include URLs and credentials, so do not print their messages or stack traces.
            System.err.println("Evaluation stopped: " + (ex instanceof IllegalArgumentException ? ex.getMessage()
                    : ex.getClass().getSimpleName() + ". Check eval configuration and logs without sharing secrets."));
            System.exit(1);
        }
    }
}
