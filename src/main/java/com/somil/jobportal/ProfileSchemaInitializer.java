package com.somil.jobportal;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/**
 * Adds profile columns before Hibernate starts. This deliberately avoids Flyway
 * because Flyway's MySQL schema-history bootstrap uses SQL unsupported by TiDB.
 */
public class ProfileSchemaInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileSchemaInitializer.class);

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Environment environment = applicationContext.getEnvironment();
        String url = environment.getProperty("spring.datasource.url");
        String username = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password", "");

        if (url == null || url.isBlank() || username == null || username.isBlank()) {
            LOGGER.info("Profile schema pre-check skipped because datasource settings are unavailable");
            return;
        }

        LOGGER.info("Checking profile database schema before JPA initialization");
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            migrate(connection);
            LOGGER.info("Profile database schema check completed");
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to prepare the profile database schema", exception);
        }
    }

    static void migrate(Connection connection) throws SQLException {
        addMissingColumns(connection, "recruiter_profile", recruiterColumns());
        addMissingColumns(connection, "job_seeker_profile", jobSeekerColumns());
    }

    private static void addMissingColumns(Connection connection,
                                          String tableName,
                                          Map<String, String> expectedColumns) throws SQLException {
        if (!tableExists(connection, tableName)) {
            return;
        }

        // One metadata round trip for the whole table: asking per column cost two
        // round trips each, which is slow against a remote database.
        Set<String> existingColumns = existingColumns(connection, tableName);

        List<String> additions = new ArrayList<>();
        for (Map.Entry<String, String> column : expectedColumns.entrySet()) {
            if (!existingColumns.contains(column.getKey().toLowerCase(Locale.ROOT))) {
                additions.add("ADD COLUMN `" + column.getKey() + "` " + column.getValue());
            }
        }

        if (additions.isEmpty()) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            if (connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("H2")) {
                for (String addition : additions) {
                    statement.executeUpdate("ALTER TABLE `" + tableName + "` " + addition);
                }
            } else {
                // TiDB accepts a consolidated ALTER and processes it much faster than
                // many separate schema changes on its distributed storage engine.
                statement.executeUpdate("ALTER TABLE `" + tableName + "` " + String.join(", ", additions));
            }
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        return hasTable(metadata, connection.getCatalog(), tableName)
                || hasTable(metadata, null, tableName);
    }

    /** Lower-cased column names already on the table, so lookups are local and case-insensitive. */
    private static Set<String> existingColumns(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        Set<String> names = collectColumns(metadata, connection.getCatalog(), tableName);
        if (names.isEmpty()) {
            names = collectColumns(metadata, null, tableName);
        }
        return names;
    }

    private static Set<String> collectColumns(DatabaseMetaData metadata, String catalog, String tableName)
            throws SQLException {
        Set<String> names = new HashSet<>();
        try (ResultSet columns = metadata.getColumns(catalog, null, tableName, null)) {
            while (columns.next()) {
                names.add(columns.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }

    private static boolean hasTable(DatabaseMetaData metadata, String catalog, String tableName) throws SQLException {
        try (ResultSet tables = metadata.getTables(catalog, null, tableName, new String[]{"TABLE"})) {
            return tables.next();
        }
    }

    private static Map<String, String> recruiterColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("designation", "VARCHAR(255) NULL");
        columns.put("industry", "VARCHAR(255) NULL");
        columns.put("company_website", "VARCHAR(255) NULL");
        columns.put("employee_count", "INT NULL");
        columns.put("office_locations", "VARCHAR(1000) NULL");
        columns.put("company_description", "VARCHAR(2000) NULL");
        columns.put("phone_number", "VARCHAR(255) NULL");
        return columns;
    }

    private static Map<String, String> jobSeekerColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("desired_job_title", "VARCHAR(255) NULL");
        columns.put("current_company", "VARCHAR(255) NULL");
        columns.put("remote_preference", "VARCHAR(255) NULL");
        columns.put("preferred_job_city", "VARCHAR(255) NULL");
        columns.put("preferred_job_state", "VARCHAR(255) NULL");
        columns.put("preferred_job_country", "VARCHAR(255) NULL");
        columns.put("willing_to_relocate", "BIT NULL");
        columns.put("preferred_locations", "VARCHAR(1000) NULL");
        columns.put("total_experience_years", "DECIMAL(4,1) NULL");
        columns.put("current_ctc", "DECIMAL(12,2) NULL");
        columns.put("expected_ctc", "DECIMAL(12,2) NULL");
        columns.put("compensation_currency", "VARCHAR(255) NULL");
        columns.put("notice_period_days", "INT NULL");
        columns.put("phone_number", "VARCHAR(255) NULL");
        columns.put("linked_in_url", "VARCHAR(255) NULL");
        columns.put("portfolio_url", "VARCHAR(255) NULL");
        columns.put("professional_headline", "VARCHAR(300) NULL");
        return columns;
    }
}
