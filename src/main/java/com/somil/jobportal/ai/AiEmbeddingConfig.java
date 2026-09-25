package com.somil.jobportal.ai;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.somil.jobportal.ai.backfill.BackfillJob;
import com.somil.jobportal.ai.backfill.EmbeddingBackfillRunner;
import com.somil.jobportal.ai.embedding.EmbeddingService;
import com.somil.jobportal.ai.index.EmbeddingIndexer;
import com.somil.jobportal.ai.index.EmbeddingUpdateQueue;
import com.somil.jobportal.ai.index.JpaEmbeddingSourceLoader;
import com.somil.jobportal.ai.schema.AiSchemaMigrator;
import com.somil.jobportal.ai.store.JdbcEmbeddingStore;
import com.somil.jobportal.repository.JobPostActivityRepository;
import com.somil.jobportal.repository.JobSeekerProfileRepository;

/**
 * Wires the semantic-matching pieces together. Everything here exists only when
 * {@code AI_EMBEDDING_ENABLED=true}; otherwise the app behaves exactly as before.
 */
@Configuration
@EnableConfigurationProperties(AiEmbeddingProperties.class)
public class AiEmbeddingConfig {

    @Configuration
    @ConditionalOnProperty(name = "app.ai.embedding.enabled", havingValue = "true")
    static class Enabled {
        private static final Logger LOGGER = LoggerFactory.getLogger(AiEmbeddingConfig.class);

        @Bean
        AiSchemaMigrator aiSchemaMigrator(DataSource dataSource, AiEmbeddingProperties properties) {
            if (properties.dimensions() != AiSchemaMigrator.VECTOR_DIMENSIONS) {
                throw new IllegalStateException("GEMINI_EMBEDDING_DIMENSIONS is " + properties.dimensions()
                        + " but the embedding columns are VECTOR(" + AiSchemaMigrator.VECTOR_DIMENSIONS
                        + "). Changing the dimension needs a new migration; see ai/README.md.");
            }
            // Fail at startup, not on the first save, if a strategy name is misspelt.
            properties.strategiesFor(EmbeddingTarget.JOB);
            properties.strategiesFor(EmbeddingTarget.CANDIDATE);
            if (!StringUtils.hasText(properties.apiKey())) {
                LOGGER.warn("AI_EMBEDDING_ENABLED is true but GEMINI_API_KEY is not set; embedding calls will fail.");
            }
            AiSchemaMigrator migrator = new AiSchemaMigrator(dataSource);
            migrator.migrate();
            return migrator;
        }

        @Bean
        EmbeddingService embeddingService(AiEmbeddingProperties properties, RestClient.Builder builder) {
            return new EmbeddingService(properties, builder);
        }

        /** Takes the migrator so the tables exist before anything reads or writes them. */
        @Bean
        JdbcEmbeddingStore embeddingStore(JdbcTemplate jdbc, AiSchemaMigrator migrated) {
            return new JdbcEmbeddingStore(jdbc);
        }

        @Bean
        JpaEmbeddingSourceLoader embeddingSourceLoader(JdbcTemplate jdbc, PlatformTransactionManager transactions,
                                                       JobPostActivityRepository jobs, JobSeekerProfileRepository profiles,
                                                       AiEmbeddingProperties properties) {
            TransactionTemplate readOnly = new TransactionTemplate(transactions);
            readOnly.setReadOnly(true);
            return new JpaEmbeddingSourceLoader(jdbc, readOnly, jobs, profiles, properties.maxChars());
        }

        @Bean
        EmbeddingIndexer embeddingIndexer(JpaEmbeddingSourceLoader loader, EmbeddingService embeddings,
                                          JdbcEmbeddingStore store) {
            return new EmbeddingIndexer(loader, embeddings, store);
        }

        @Bean(destroyMethod = "close")
        EmbeddingUpdateQueue embeddingUpdateQueue(EmbeddingIndexer indexer, AiEmbeddingProperties properties) {
            return new EmbeddingUpdateQueue(indexer, properties);
        }

        @Bean
        @Profile("backfill")
        EmbeddingBackfillRunner embeddingBackfillRunner(JpaEmbeddingSourceLoader loader, EmbeddingIndexer indexer,
                                                        EmbeddingService embeddings, AiEmbeddingProperties properties,
                                                        Environment environment, ConfigurableApplicationContext context) {
            return new EmbeddingBackfillRunner(new BackfillJob(loader, indexer), embeddings, properties, environment, context);
        }
    }
}
