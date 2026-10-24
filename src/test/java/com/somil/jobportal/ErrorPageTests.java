package com.somil.jobportal;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:errorpages;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "server.error.include-message=never", "server.error.include-stacktrace=never",
        "server.error.whitelabel.enabled=false"})
@Import(ErrorPageTests.Failures.class)
class ErrorPageTests {
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @Test void browserErrorsKeepTheirStatusAndShowRecoveryLinks() throws Exception {
        check("/global-search/missing-page", "GET", 404);
        check("/register", "PUT", 405);
        check("/register/new", "POST", 403); // missing CSRF token
        check("/global-search/test-failure", "GET", 500);
    }

    private void check(String path, String method, int status) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "text/html").method(method, HttpRequest.BodyPublishers.noBody()).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.body()).contains("HotDevJobs", "Back to home", "Create account")
                .doesNotContain("Whitelabel", "private-diagnostic", "IllegalStateException");
    }

    @TestConfiguration
    static class Failures {
        @Bean FailureController failureController() { return new FailureController(); }
    }
    @RestController
    static class FailureController {
        @GetMapping("/global-search/test-failure")
        String fail() { throw new IllegalStateException("private-diagnostic"); }
    }
}
