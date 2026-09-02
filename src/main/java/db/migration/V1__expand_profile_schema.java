package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V1__expand_profile_schema extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        addMissingColumns(connection, "recruiter_profile", recruiterColumns());
        addMissingColumns(connection, "job_seeker_profile", jobSeekerColumns());
    }

    private void addMissingColumns(Connection connection,
                                   String tableName,
                                   Map<String, String> expectedColumns) throws SQLException {
        List<String> additions = new ArrayList<>();

        for (Map.Entry<String, String> column : expectedColumns.entrySet()) {
            if (!columnExists(connection, tableName, column.getKey())) {
                additions.add("ADD COLUMN `" + column.getKey() + "` " + column.getValue());
            }
        }

        if (additions.isEmpty()) {
            return;
        }

        String sql = "ALTER TABLE `" + tableName + "` " + String.join(", ", additions);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        String catalog = connection.getCatalog();

        if (hasColumn(metadata, catalog, tableName, columnName)) {
            return true;
        }

        return hasColumn(metadata, null, tableName, columnName);
    }

    private boolean hasColumn(DatabaseMetaData metadata,
                              String catalog,
                              String tableName,
                              String columnName) throws SQLException {
        try (ResultSet columns = metadata.getColumns(catalog, null, tableName, columnName)) {
            return columns.next();
        }
    }

    private Map<String, String> recruiterColumns() {
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

    private Map<String, String> jobSeekerColumns() {
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
