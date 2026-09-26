-- Test migration: H2-compatible stand-in for the real V1.
CREATE TABLE IF NOT EXISTS migration_one (id INT PRIMARY KEY);
INSERT INTO migration_one VALUES (1);
