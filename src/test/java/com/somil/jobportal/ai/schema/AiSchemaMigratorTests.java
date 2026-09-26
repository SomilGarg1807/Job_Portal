package com.somil.jobportal.ai.schema;

import java.sql.Connection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.*;

class AiSchemaMigratorTests {
    private static DriverManagerDataSource h2() {
        return new DriverManagerDataSource("jdbc:h2:mem:migrate-" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
    }

    @Test
    void appliesEachVersionOnceInOrder() throws Exception {
        var dataSource = h2();
        var migrations = AiSchemaMigrator.discover("classpath*:db/ai-test/V*__*.sql");
        assertEquals(List.of(1, 2), migrations.stream().map(AiSchemaMigrator.Migration::version).toList());

        try (Connection connection = dataSource.getConnection()) {
            assertEquals(2, AiSchemaMigrator.apply(connection, migrations));
            // A second run (say, the next app start) changes nothing and would fail on the INSERT if it re-ran V1.
            assertEquals(0, AiSchemaMigrator.apply(connection, migrations));
        }
        var jdbc = new JdbcTemplate(dataSource);
        assertEquals(List.of(1, 2), jdbc.queryForList("SELECT version FROM ai_schema_version ORDER BY version", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM migration_one", Integer.class));
    }

    @Test
    void skipsH2BecauseItHasNoVectorType() {
        var dataSource = h2();
        new AiSchemaMigrator(dataSource).migrate();
        assertEquals(0, new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'ai_schema_version'", Integer.class));
    }

    @Test
    void realV1CreatesBothVectorTablesWithTheConfiguredDimension() throws Exception {
        var migrations = AiSchemaMigrator.discover(AiSchemaMigrator.DEFAULT_LOCATION);
        assertEquals(1, migrations.get(0).version());
        List<String> statements = AiSchemaMigrator.statements(migrations.get(0).sql());
        assertEquals(2, statements.size());
        assertTrue(statements.get(0).startsWith("CREATE TABLE IF NOT EXISTS ai_job_embedding"));
        assertTrue(statements.get(1).startsWith("CREATE TABLE IF NOT EXISTS ai_candidate_embedding"));
        statements.forEach(sql -> assertTrue(sql.contains("VECTOR(" + AiSchemaMigrator.VECTOR_DIMENSIONS + ")")));
    }

    @Test
    void splitsStatementsAndDropsComments() {
        assertEquals(List.of("CREATE TABLE a (\n  id INT\n)", "INSERT INTO a VALUES (1)"),
                AiSchemaMigrator.statements("-- comment\nCREATE TABLE a (\n  id INT\n);\n\nINSERT INTO a VALUES (1);\n"));
    }
}
