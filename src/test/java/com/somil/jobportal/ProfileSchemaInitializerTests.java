package com.somil.jobportal;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

class ProfileSchemaInitializerTests {

    @Test
    void addsAllMissingColumnsAndCanRunTwice() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:schema-migration-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE recruiter_profile (user_account_id INT PRIMARY KEY)");
            statement.execute("CREATE TABLE job_seeker_profile (user_account_id INT PRIMARY KEY)");

            ProfileSchemaInitializer.migrate(connection);
            ProfileSchemaInitializer.migrate(connection);

            assertTrue(columnExists(connection, "recruiter_profile", "company_description"));
            assertTrue(columnExists(connection, "recruiter_profile", "employee_count"));
            assertTrue(columnExists(connection, "job_seeker_profile", "desired_job_title"));
            assertTrue(columnExists(connection, "job_seeker_profile", "expected_ctc"));
            assertTrue(columnExists(connection, "job_seeker_profile", "professional_headline"));
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws Exception {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null, table, column)) {
            return columns.next();
        }
    }
}
