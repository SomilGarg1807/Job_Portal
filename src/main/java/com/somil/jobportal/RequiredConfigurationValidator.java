package com.somil.jobportal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/**
 * Stops startup with a readable message when a required setting is missing, so a bad
 * deployment fails immediately instead of booting and then failing with a driver error
 * or a 401 from a provider once a user hits the feature.
 *
 * <p>Only settings the application genuinely cannot run without are fatal. Optional
 * integrations (AI, email, cache) already disable themselves cleanly, so they are logged
 * as warnings instead.
 */
public class RequiredConfigurationValidator implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequiredConfigurationValidator.class);

    /** Resolved property -> the environment variable a developer is expected to set. */
    private static final Map<String, String> REQUIRED = Map.of(
            "spring.datasource.url", "DB_URL",
            "spring.datasource.username", "DB_USERNAME",
            "spring.datasource.password", "DB_PASSWORD");

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Environment environment = applicationContext.getEnvironment();

        List<String> missing = new ArrayList<>();
        // Sorted so the message reads the same way on every boot.
        REQUIRED.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .filter(entry -> !hasText(resolve(environment, entry.getKey())))
                .forEach(entry -> missing.add(entry.getValue()));

        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing required configuration: " + String.join(", ", missing)
                    + ". Set these as environment variables (on Render: the service's Environment tab),"
                    + " or copy application-local.properties.example to application-local.properties for"
                    + " local development. See the Configuration section of README.md.");
        }

        warnAboutOptional(environment);
    }

    private static void warnAboutOptional(Environment environment) {
        Map<String, String> optional = new LinkedHashMap<>();
        optional.put("GEMINI_API_KEY", "the AI assistant and resume comparison are disabled");
        optional.put("REMEMBER_ME_KEY", "remember-me logins will not survive a restart");
        optional.forEach((name, consequence) -> {
            if (!hasText(resolve(environment, name))) {
                LOGGER.warn("{} is not set; {}.", name, consequence);
            }
        });

        boolean resend = hasText(resolve(environment, "RESEND_API_KEY")) && hasText(resolve(environment, "EMAIL_FROM"));
        boolean gmail = hasText(resolve(environment, "GMAIL_CLIENT_ID"))
                && hasText(resolve(environment, "GMAIL_CLIENT_SECRET"))
                && hasText(resolve(environment, "GMAIL_REFRESH_TOKEN"));
        if (!resend && !gmail) {
            LOGGER.warn("No email provider is configured (RESEND_API_KEY + EMAIL_FROM, or the GMAIL_* variables);"
                    + " new users cannot complete signup.");
        }
    }

    /**
     * An unset variable behind a nested placeholder such as ${DB_URL:${LOCAL_DB_URL}} raises
     * a resolution error rather than returning null, so treat that as "missing" too.
     */
    private static String resolve(Environment environment, String key) {
        try {
            return environment.getProperty(key);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
