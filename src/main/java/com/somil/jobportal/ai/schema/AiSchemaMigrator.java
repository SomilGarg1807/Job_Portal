package com.somil.jobportal.ai.schema;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Applies the versioned scripts in {@code src/main/resources/db/ai} ({@code V1__name.sql},
 * {@code V2__name.sql}, ...) once each, in order, and records them in {@code ai_schema_version}.
 *
 * <p>This is a deliberately small stand-in for Flyway, which this project avoids because
 * Flyway's MySQL bootstrap SQL is not supported by TiDB (see ProfileSchemaInitializer).
 * Scripts should still be idempotent ({@code CREATE TABLE IF NOT EXISTS}) so that two
 * processes starting at once, such as the website and a laptop backfill, are harmless.
 * A script that has been applied must never be edited; add a new version instead.
 */
public class AiSchemaMigrator {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiSchemaMigrator.class);
    private static final Pattern FILE_NAME = Pattern.compile("V(\\d+)__(.+)\\.sql");

    /** The dimension V1 declares for the vector columns. */
    public static final int VECTOR_DIMENSIONS = 768;
    public static final String DEFAULT_LOCATION = "classpath*:db/ai/V*__*.sql";

    record Migration(int version, String description, String sql) {
    }

    private final DataSource dataSource;
    private final String location;

    public AiSchemaMigrator(DataSource dataSource) {
        this(dataSource, DEFAULT_LOCATION);
    }

    AiSchemaMigrator(DataSource dataSource, String location) {
        this.dataSource = dataSource;
        this.location = location;
    }

    public void migrate() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("H2")) {
                // H2 (used by the tests) has no VECTOR type.
                LOGGER.info("AI schema migration skipped: H2 has no VECTOR type");
                return;
            }
            int applied = apply(connection, discover(location));
            LOGGER.info("AI schema is up to date ({} new migration(s) applied)", applied);
        } catch (SQLException | IOException ex) {
            throw new IllegalStateException("Could not apply the AI schema migrations in " + location, ex);
        }
    }

    static List<Migration> discover(String location) throws IOException {
        List<Migration> migrations = new ArrayList<>();
        for (Resource resource : new PathMatchingResourcePatternResolver().getResources(location)) {
            Matcher name = FILE_NAME.matcher(String.valueOf(resource.getFilename()));
            if (!name.matches()) continue;
            migrations.add(new Migration(Integer.parseInt(name.group(1)), name.group(2).replace('_', ' '),
                    resource.getContentAsString(StandardCharsets.UTF_8)));
        }
        migrations.sort(Comparator.comparingInt(Migration::version));
        for (int i = 1; i < migrations.size(); i++) {
            if (migrations.get(i).version() == migrations.get(i - 1).version()) {
                throw new IllegalStateException("Two AI migrations share version " + migrations.get(i).version());
            }
        }
        return migrations;
    }

    static int apply(Connection connection, List<Migration> migrations) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS ai_schema_version ("
                    + "version INT NOT NULL PRIMARY KEY, description VARCHAR(200) NOT NULL, "
                    + "applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        }
        Set<Integer> done = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT version FROM ai_schema_version")) {
            while (rows.next()) done.add(rows.getInt(1));
        }
        int applied = 0;
        for (Migration migration : migrations) {
            if (done.contains(migration.version())) continue;
            LOGGER.info("Applying AI migration V{}: {}", migration.version(), migration.description());
            try (Statement statement = connection.createStatement()) {
                for (String sql : statements(migration.sql())) statement.execute(sql);
            }
            // Record it unless another process (say, the website starting at the same time) already did.
            try (PreparedStatement record = connection.prepareStatement(
                    "INSERT INTO ai_schema_version (version, description) SELECT ?, ? FROM (SELECT 1 AS x) one "
                            + "WHERE NOT EXISTS (SELECT 1 FROM ai_schema_version WHERE version = ?)")) {
                record.setInt(1, migration.version());
                record.setString(2, migration.description());
                record.setInt(3, migration.version());
                record.executeUpdate();
            }
            applied++;
        }
        return applied;
    }

    /** Splits a script on semicolons that end a line, dropping {@code --} comment lines. */
    static List<String> statements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) continue;
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                String sql = current.toString().trim();
                statements.add(sql.substring(0, sql.length() - 1).trim());
                current.setLength(0);
            }
        }
        if (!current.toString().isBlank()) statements.add(current.toString().trim());
        return statements;
    }
}
