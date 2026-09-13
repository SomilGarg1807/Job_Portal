package com.somil.jobportal.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;

@Service
public class GmailOAuthService {
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final RestClient client;
    private final SecureRandom random = new SecureRandom();

    public GmailOAuthService(@Value("${GMAIL_CLIENT_ID:}") String clientId,
                             @Value("${GMAIL_CLIENT_SECRET:}") String clientSecret,
                             @Value("${GMAIL_REDIRECT_URI:}") String redirectUri,
                             RestClient.Builder builder) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
        this.redirectUri = redirectUri == null ? "" : redirectUri.trim();
        this.client = builder.build();
    }

    public boolean configured() {
        return !clientId.isBlank() && !clientSecret.isBlank() && !redirectUri.isBlank();
    }

    public String newState() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public String authorizationUrl(String state) {
        if (!configured()) throw new IllegalStateException("Gmail OAuth is not configured yet.");
        return "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&response_type=code"
                + "&scope=" + encode("https://www.googleapis.com/auth/gmail.send")
                + "&access_type=offline"
                + "&prompt=consent"
                + "&state=" + encode(state);
    }

    public String exchange(String code) {
        if (!configured()) throw new IllegalStateException("Gmail OAuth is not configured yet.");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Google did not return an authorization code.");
        @SuppressWarnings("unchecked")
        Map<String, Object> response = client.post().uri("https://oauth2.googleapis.com/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("code=" + encode(code)
                        + "&client_id=" + encode(clientId)
                        + "&client_secret=" + encode(clientSecret)
                        + "&redirect_uri=" + encode(redirectUri)
                        + "&grant_type=authorization_code")
                .retrieve().body(Map.class);
        Object token = response == null ? null : response.get("refresh_token");
        if (!(token instanceof String refreshToken) || refreshToken.isBlank()) {
            throw new IllegalStateException("Google did not return a refresh token. Start again and approve access with prompt=consent.");
        }
        return refreshToken;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
