package com.somil.jobportal.ai.backfill;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

/** The backfill profile boots without a web server and runs the job (a dry run here). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"ai.backfill.exit=false", "ai.backfill.dry-run=true"})
@ActiveProfiles("backfill")
@ExtendWith(OutputCaptureExtension.class)
class BackfillProfileTests {
    @Autowired ApplicationContext context;

    @Test
    void runsAsACommandLineTask(CapturedOutput output) {
        assertEquals(1, context.getBeansOfType(EmbeddingBackfillRunner.class).size());
        assertFalse(context.containsBean("dispatcherServlet"));
        assertTrue(output.getOut().contains("Gemini usage for this run: requests=0"), "runner must have finished");
    }
}
