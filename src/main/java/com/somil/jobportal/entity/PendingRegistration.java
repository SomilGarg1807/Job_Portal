package com.somil.jobportal.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Credentials remain hashed; this record cannot be used to sign in. */
@Entity
public class PendingRegistration {
    @Id @Column(length = 254) public String email;
    @Column(nullable = false, unique = true, length = 36) public String token;
    @Column(nullable = false, length = 100) public String passwordHash;
    @Column(nullable = false, length = 100) public String codeHash;
    public int userTypeId;
    public Instant expiresAt;
    public Instant lastSentAt;
    public Instant windowStartedAt;
    public int sendCount;
    public int failedAttempts;
    public boolean consumed;
    @Version public Long version;
}
