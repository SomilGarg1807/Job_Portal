package com.somil.jobportal.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Database probe for an external monitor (e.g. UptimeRobot every 5 minutes): runs a trivial
 * query so TiDB and the connection pool stay warm, and reports 503 if the database is unreachable.
 * Kept separate from /health so a database hiccup never fails Render's own health check.
 */
@RestController
public class DatabaseHealthController {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHealthController.class);

    private final JdbcTemplate jdbc;

    public DatabaseHealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping(value = "/health/db", produces = "application/json")
    public ResponseEntity<Map<String, Object>> database() {
        long start = System.nanoTime();
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            long millis = (System.nanoTime() - start) / 1_000_000;
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(Map.of("status", "UP", "database", "UP", "latencyMs", millis));
        } catch (RuntimeException ex) {
            log.warn("Database health check failed: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(CacheControl.noStore())
                    .body(Map.of("status", "DOWN", "database", "DOWN"));
        }
    }
}
