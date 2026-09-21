package com.somil.jobportal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

class RequiredConfigurationValidatorTests {

    private final RequiredConfigurationValidator validator = new RequiredConfigurationValidator();

    @Test
    void namesEveryMissingDatabaseVariable() {
        assertThatThrownBy(() -> validator.initialize(contextWith(new MockEnvironment())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_URL")
                .hasMessageContaining("DB_USERNAME")
                .hasMessageContaining("DB_PASSWORD")
                .hasMessageContaining("application-local.properties");
    }

    @Test
    void namesOnlyTheVariableThatIsMissing() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://localhost:3306/jobportal")
                .withProperty("spring.datasource.username", "portal");

        assertThatThrownBy(() -> validator.initialize(contextWith(environment)))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(error -> assertThat(error.getMessage()).contains("DB_PASSWORD").doesNotContain("DB_URL"));
    }

    @Test
    void passesWhenTheDatabaseIsConfiguredEvenWithoutOptionalIntegrations() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://localhost:3306/jobportal")
                .withProperty("spring.datasource.username", "portal")
                .withProperty("spring.datasource.password", "not-a-real-password");

        assertThatCode(() -> validator.initialize(contextWith(environment))).doesNotThrowAnyException();
    }

    private static GenericApplicationContext contextWith(MockEnvironment environment) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(environment);
        return context;
    }
}
