package com.somil.jobportal.eval;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Reads only EVAL_* credentials; never falls back to the application's DB_URL. */
final class EvalConfig {
    final String url;
    final String username;
    final String password;
    final String apiKey;
    final String database;
    final String model;
    final int dimensions;
    final int batchSize;

    private EvalConfig(Map<String, String> env) {
        url = required(env, "EVAL_DB_URL");
        username = required(env, "EVAL_DB_USERNAME");
        password = required(env, "EVAL_DB_PASSWORD");
        apiKey = required(env, "GEMINI_API_KEY");
        model = env.getOrDefault("EVAL_EMBEDDING_MODEL", "gemini-embedding-001");
        dimensions = Integer.parseInt(env.getOrDefault("EVAL_EMBEDDING_DIMENSIONS", "768"));
        batchSize = Integer.parseInt(env.getOrDefault("EVAL_EMBEDDING_BATCH_SIZE", "50"));
        if (dimensions != 768 || batchSize < 1 || batchSize > 100 || !"gemini-embedding-001".equals(model))
            throw new IllegalArgumentException("Eval model must be gemini-embedding-001 at 768 dimensions; batch size must be 1..100.");
        try {
            if (!url.startsWith("jdbc:mysql://")) throw new IllegalArgumentException();
            URI uri = URI.create(url.substring(5));
            database = uri.getPath().replaceFirst("^/", "");
            if (!database.matches("[A-Za-z0-9_]*eval[A-Za-z0-9_]*")) throw new IllegalArgumentException();
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("EVAL_DB_URL must be a JDBC MySQL URL whose database name contains 'eval'. URL omitted.");
        }
    }

    static EvalConfig load(Path dotenv) throws Exception {
        Map<String, String> values = new HashMap<>();
        if (Files.exists(dotenv)) {
            for (String line : Files.readAllLines(dotenv)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int equals = trimmed.indexOf('=');
                if (equals < 1) throw new IllegalArgumentException("Invalid .env.eval line (value omitted).");
                String value = trimmed.substring(equals + 1).trim();
                if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) value = value.substring(1, value.length() - 1);
                values.put(trimmed.substring(0, equals).trim(), value);
            }
        }
        // Environment variables take precedence over the local, ignored file.
        for (String name : new String[]{"EVAL_DB_URL", "EVAL_DB_USERNAME", "EVAL_DB_PASSWORD", "GEMINI_API_KEY",
                "EVAL_EMBEDDING_MODEL", "EVAL_EMBEDDING_DIMENSIONS", "EVAL_EMBEDDING_BATCH_SIZE"}) {
            String value = System.getenv(name);
            if (value != null && !value.isBlank()) values.put(name, value);
        }
        return new EvalConfig(values);
    }

    static EvalConfig from(Map<String, String> values) { return new EvalConfig(values); }

    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required for eval.");
        return value;
    }
}
