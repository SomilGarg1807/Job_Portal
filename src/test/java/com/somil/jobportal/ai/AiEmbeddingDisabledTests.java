package com.somil.jobportal.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import com.somil.jobportal.ai.embedding.EmbeddingService;
import com.somil.jobportal.ai.index.EmbeddingUpdateQueue;
import com.somil.jobportal.ai.schema.AiSchemaMigrator;

import static org.junit.jupiter.api.Assertions.*;

/** Embedding is off unless switched on, so deploying this code alone changes nothing. */
@SpringBootTest
class AiEmbeddingDisabledTests {
    @Autowired ApplicationContext context;

    @Test
    void noEmbeddingBeansExistByDefault() {
        assertTrue(context.getBeansOfType(EmbeddingUpdateQueue.class).isEmpty());
        assertTrue(context.getBeansOfType(EmbeddingService.class).isEmpty());
        assertTrue(context.getBeansOfType(AiSchemaMigrator.class).isEmpty());
        assertFalse(context.getBean(AiEmbeddingProperties.class).enabled());
    }
}
