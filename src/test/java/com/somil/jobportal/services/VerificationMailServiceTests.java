package com.somil.jobportal.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class VerificationMailServiceTests {
    private MockRestServiceServer server;
    private RestClient.Builder builder;

    @BeforeEach
    void setup() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void gmailConfigurationRefreshesTokenAndSendsEncodedMessage() {
        RestClient client = builder.build();
        var service = new VerificationMailService("", "", "client-id", "client-secret",
                "refresh-token", "hotdevjobs.co@gmail.com", client);
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(content().string(containsString("grant_type=refresh_token")))
                .andExpect(content().string(containsString("client_id=client-id")))
                .andRespond(withSuccess("{\"access_token\":\"access-token\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://gmail.googleapis.com/gmail/v1/users/me/messages/send"))
                .andExpect(header("Authorization", "Bearer access-token"))
                .andExpect(content().string(containsString("\"raw\"")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        service.send("candidate@example.com", "123456", "delivery-1");
        server.verify();
    }

    @Test
    void resendRemainsFallbackWhenGmailRefreshTokenIsMissing() {
        RestClient client = builder.build();
        var service = new VerificationMailService("resend-key", "HotDevJobs <verify@example.com>",
                "client-id", "client-secret", "", "hotdevjobs.co@gmail.com", client);
        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(header("Authorization", "Bearer resend-key"))
                .andExpect(header("Idempotency-Key", "delivery-1"))
                .andExpect(content().string(containsString("123456")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        service.send("candidate@example.com", "123456", "delivery-1");
        server.verify();
    }
}
