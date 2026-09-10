package com.somil.jobportal.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AiAssistantServiceTests {
    private MockRestServiceServer server;
    private AiAssistantService service;

    @BeforeEach
    void setup() {
        var builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com/v1beta");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new AiAssistantService(builder.build(), "test-key", "gemini-3.5-flash-lite");
    }

    @Test
    void sendsRoleSpecificPromptAndCombinesOnlyVisibleText() {
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(content().string(containsString("inclusive job description")))
                .andRespond(withSuccess("""
                    {"candidates":[{"finishReason":"STOP","content":{"parts":[
                    {"text":"internal", "thought":true},{"text":"Role summary"},{"text":" and skills"}]}}]}
                    """, MediaType.APPLICATION_JSON));
        assertEquals("Role summary and skills", service.assist("Java developer with Spring Boot", true));
        server.verify();
    }

    @Test
    void unavailableConfigurationMakesNoNetworkRequest() {
        var unconfigured = new AiAssistantService(RestClient.create(), "", "unused");
        var error = assertThrows(AiAssistantService.AssistantException.class, () -> unconfigured.assist("Java developer", false));
        assertEquals(503, error.getStatus());
        server.verify();
    }

    @Test
    void providerQuotaErrorIsSanitized() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("provider-private-detail"));
        var error = assertThrows(AiAssistantService.AssistantException.class, () -> service.assist("Java developer", false));
        assertEquals(429, error.getStatus());
        assertFalse(error.getMessage().contains("provider-private-detail"));
    }

    @Test
    void incompleteOrBlockedResponseIsNotPresentedAsACompletedDraft() {
        server.expect(anything()).andRespond(withSuccess("{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}", MediaType.APPLICATION_JSON));
        var error = assertThrows(AiAssistantService.AssistantException.class, () -> service.assist("Java developer", false));
        assertEquals(502, error.getStatus());
    }

    @Test
    void connectionFailureHasAnActionableMessage() {
        server.expect(anything()).andRespond(withException(new java.net.SocketTimeoutException()));
        var error = assertThrows(AiAssistantService.AssistantException.class, () -> service.assist("Java developer", false));
        assertEquals(502, error.getStatus());
        assertTrue(error.getMessage().contains("try again"));
    }
}
