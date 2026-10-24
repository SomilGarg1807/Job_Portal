package com.somil.jobportal.services;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;

@EnabledIfSystemProperty(named = "mail.simulation", matches = "true")
@ExtendWith(OutputCaptureExtension.class)
class VerificationMailSimulationTests {
    private final List<String> requests = new ArrayList<>();

    private VerificationMailService service(String scenario, boolean fallback) {
        var builder = RestClient.builder().requestInterceptor((request, body, execution) -> {
            requests.add(request.getURI().getHost());
            request.getHeaders().set("x-mail-case", scenario);
            // Redirect only in this test; preserve the real HTTP client, body, headers and timeouts.
            return execution.execute(new HttpRequestWrapper(request) {
                @Override public URI getURI() {
                    return URI.create(System.getProperty("mail.simulation.url", "http://127.0.0.1:4900/mail")
                            + request.getURI().getPath());
                }
            }, body);
        });
        return new VerificationMailService(fallback ? "fake-resend-key" : "", fallback ? "verify@example.com" : "",
                "fake-client", "fake-secret", "fake-refresh", "sender@example.com", builder);
    }

    @Test void healthyGmailDoesNotCallFallback() {
        service("success", true).send("candidate@example.com", "123456", "delivery-1");
        assertThat(requests).containsExactly("oauth2.googleapis.com", "gmail.googleapis.com");
    }

    @Test void expiredGmailTokenFallsBackAndLogsOnlySafeReason(CapturedOutput output) {
        service("expired", true).send("candidate@example.com", "123456", "delivery-2");
        assertThat(requests).containsExactly("oauth2.googleapis.com", "api.resend.com");
        assertThat(output).contains("stage=gmail-token status=400 reason=invalid_grant")
                .doesNotContain("private-provider-detail", "fake-secret", "fake-refresh", "candidate@example.com", "123456");
    }

    @Test void gmailSendFailureFallsBackForPasswordResetToo() {
        service("gmail-down", true).sendPasswordReset("candidate@example.com", "123456", "delivery-3");
        assertThat(requests).containsExactly("oauth2.googleapis.com", "gmail.googleapis.com", "api.resend.com");
    }

    @Test void missingTokenAndHtmlErrorsAlsoFallBack() {
        service("missing-token", true).send("candidate@example.com", "123456", "delivery-4");
        service("html-error", true).send("candidate@example.com", "123456", "delivery-5");
        assertThat(requests).containsExactly("oauth2.googleapis.com", "api.resend.com",
                "oauth2.googleapis.com", "gmail.googleapis.com", "api.resend.com");
    }

    @Test void allProvidersFailWithSafeMessage(CapturedOutput output) {
        assertThatThrownBy(() -> service("both-fail", true).send("candidate@example.com", "123456", "delivery-6"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Please try again later");
        assertThat(output).contains("stage=resend-send status=403").doesNotContain("private-provider-detail");
    }

    @Test void gmailFailureWithoutFallbackStaysAnError() {
        assertThatThrownBy(() -> service("expired", false).send("candidate@example.com", "123456", "delivery-7"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Please try again later");
        assertThat(requests).containsExactly("oauth2.googleapis.com");
    }

    @Test void gmailTimeoutUsesConfiguredFallback() {
        service("timeout", true).send("candidate@example.com", "123456", "delivery-8");
        assertThat(requests).containsExactly("oauth2.googleapis.com", "gmail.googleapis.com", "api.resend.com");
    }
}
