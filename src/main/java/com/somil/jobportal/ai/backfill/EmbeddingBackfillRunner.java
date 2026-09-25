package com.somil.jobportal.ai.backfill;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import com.somil.jobportal.ai.AiEmbeddingProperties;
import com.somil.jobportal.ai.EmbeddingStrategy;
import com.somil.jobportal.ai.EmbeddingTarget;
import com.somil.jobportal.ai.embedding.EmbeddingService;

/**
 * Command-line entry point, active only with the {@code backfill} Spring profile. Starts the
 * app without a web server, runs the backfill, prints a usage summary and exits.
 *
 * <pre>
 * .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=backfill" "-Dspring-boot.run.arguments=--ai.backfill.dry-run=true"
 * </pre>
 *
 * Options (all optional): {@code --ai.backfill.target=all|jobs|candidates},
 * {@code --ai.backfill.strategies=job_full_v1,cand_profile_v1}, {@code --ai.backfill.from-id=0},
 * {@code --ai.backfill.max-rows=0} (0 = all), {@code --ai.backfill.dry-run=false}.
 */
public class EmbeddingBackfillRunner implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddingBackfillRunner.class);

    private final BackfillJob job;
    private final EmbeddingService embeddings;
    private final AiEmbeddingProperties properties;
    private final Environment environment;
    private final ConfigurableApplicationContext context;

    public EmbeddingBackfillRunner(BackfillJob job, EmbeddingService embeddings, AiEmbeddingProperties properties,
                                   Environment environment, ConfigurableApplicationContext context) {
        this.job = job;
        this.embeddings = embeddings;
        this.properties = properties;
        this.environment = environment;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = BackfillJob.OK;
        for (BackfillJob.Options options : options()) {
            BackfillJob.Outcome outcome = job.run(options);
            if (outcome.exitCode() != BackfillJob.OK) {
                exitCode = outcome.exitCode();
                break;
            }
        }
        LOGGER.info("Gemini usage for this run: {}", embeddings.usage());
        if (environment.getProperty("ai.backfill.exit", Boolean.class, true)) {
            int code = exitCode;
            System.exit(SpringApplication.exit(context, () -> code));
        }
    }

    List<BackfillJob.Options> options() {
        String target = environment.getProperty("ai.backfill.target", "all").trim().toLowerCase(Locale.ROOT);
        List<EmbeddingTarget> targets = switch (target) {
            case "all" -> List.of(EmbeddingTarget.JOB, EmbeddingTarget.CANDIDATE);
            case "jobs", "job" -> List.of(EmbeddingTarget.JOB);
            case "candidates", "candidate" -> List.of(EmbeddingTarget.CANDIDATE);
            default -> throw new IllegalArgumentException("--ai.backfill.target must be all, jobs or candidates");
        };
        String requested = environment.getProperty("ai.backfill.strategies", "");
        List<EmbeddingStrategy> chosen = Arrays.stream(requested.split(",")).filter(s -> !s.isBlank())
                .map(EmbeddingStrategy::fromId).toList();

        List<BackfillJob.Options> options = new ArrayList<>();
        for (EmbeddingTarget each : targets) {
            List<EmbeddingStrategy> strategies = chosen.isEmpty() ? properties.strategiesFor(each)
                    : chosen.stream().filter(s -> s.target() == each).toList();
            if (strategies.isEmpty()) continue;
            options.add(new BackfillJob.Options(each, strategies,
                    environment.getProperty("ai.backfill.from-id", Integer.class, 0),
                    environment.getProperty("ai.backfill.max-rows", Integer.class, 0),
                    properties.batchSize(),
                    environment.getProperty("ai.backfill.dry-run", Boolean.class, false)));
        }
        return options;
    }
}
