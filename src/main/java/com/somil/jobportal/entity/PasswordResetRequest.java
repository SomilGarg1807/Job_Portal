package com.somil.jobportal.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Password-reset OTP storage. Codes are hashed; this record cannot be used to sign in. */
@Entity
public class PasswordResetRequest {
    @Id @Column(length = 254) public String email;
    @Column(nullable = false, unique = true, length = 36) public String token;
    @Column(nullable = false, length = 100) public String codeHash;
    public Instant expiresAt;
    public Instant lastSentAt;
    public Instant windowStartedAt;
    public int sendCount;
    public int failedAttempts;
    public boolean codeVerified;
    public boolean consumed;
    @Version public Long version;
}
