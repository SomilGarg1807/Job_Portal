package com.somil.jobportal.services;

import com.somil.jobportal.entity.PasswordResetRequest;
import com.somil.jobportal.entity.Users;
import com.somil.jobportal.repository.PasswordResetRequestRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class PasswordResetService {
    public static class InvalidCode extends RuntimeException {
        public InvalidCode(String message) { super(message); }
    }

    private final PasswordResetRequestRepository resets;
    private final UsersService users;
    private final PasswordEncoder encoder;
    private final VerificationMailService mail;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(PasswordResetRequestRepository resets, UsersService users,
                                PasswordEncoder encoder, VerificationMailService mail) {
        this.resets = resets;
        this.users = users;
        this.encoder = encoder;
        this.mail = mail;
    }

    /**
     * Starts a reset when the email belongs to a job seeker or recruiter.
     * Returns a session token when mail is sent, or empty when no account should be revealed.
     */
    @Transactional
    public String start(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new IllegalArgumentException("Enter a valid email address.");
        if (users.getUserByEmail(email).isEmpty())
            return "";
        PasswordResetRequest record = resets.lockEmail(email).orElseGet(PasswordResetRequest::new);
        checkResend(record);
        record.email = email;
        record.token = UUID.randomUUID().toString();
        record.codeVerified = false;
        record.consumed = false;
        send(record);
        resets.save(record);
        return record.token;
    }

    @Transactional
    public void resend(String token) {
        PasswordResetRequest record = active(token);
        if (record.codeVerified)
            throw new IllegalArgumentException("This reset code was already verified. Choose a new password.");
        checkResend(record);
        send(record);
    }

    @Transactional(noRollbackFor = InvalidCode.class)
    public void verify(String token, String code) {
        PasswordResetRequest record = active(token);
        if (record.codeVerified) return;
        if (record.expiresAt.isBefore(Instant.now())) throw new InvalidCode("This code has expired. Request a new code.");
        if (record.failedAttempts >= 5) throw new InvalidCode("Too many attempts. Try again after one hour.");
        if (code == null || !code.matches("[0-9]{6}") || !encoder.matches(code, record.codeHash)) {
            record.failedAttempts++;
            throw new InvalidCode("Incorrect reset code. " + (5 - record.failedAttempts) + " attempts remaining.");
        }
        if (users.getUserByEmail(record.email).isEmpty())
            throw new InvalidCode("Start password reset again.");
        record.codeVerified = true;
        record.codeHash = "verified";
    }

    @Transactional
    public void resetPassword(String token, String password) {
        PasswordResetRequest record = active(token);
        if (!record.codeVerified) throw new InvalidCode("Verify the email code before choosing a new password.");
        if (password == null || password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("Use a password with at least 8 characters and at most 72 bytes.");
        Users account = users.getUserByEmail(record.email)
                .orElseThrow(() -> new InvalidCode("Start password reset again."));
        users.updatePassword(account, password);
        record.consumed = true;
        record.codeVerified = false;
        record.codeHash = "consumed";
    }

    private PasswordResetRequest active(String token) {
        if (token == null || token.isBlank())
            throw new InvalidCode("Incorrect or expired reset code. Request a new code.");
        PasswordResetRequest record = resets.lockToken(token)
                .orElseThrow(() -> new InvalidCode("Incorrect or expired reset code. Request a new code."));
        if (record.consumed) throw new InvalidCode("This reset link has already been used. Please sign in.");
        return record;
    }

    private void checkResend(PasswordResetRequest record) {
        Instant now = Instant.now();
        if (record.lastSentAt != null && now.isBefore(record.lastSentAt.plusSeconds(60)))
            throw new IllegalArgumentException("Please wait 60 seconds before requesting another code.");
        if (record.windowStartedAt == null || !now.isBefore(record.windowStartedAt.plusSeconds(3600))) {
            record.windowStartedAt = now;
            record.sendCount = 0;
            record.failedAttempts = 0;
        }
        if (record.sendCount >= 5 || record.failedAttempts >= 5)
            throw new IllegalArgumentException("Password reset limit reached. Please try again after one hour.");
    }

    private void send(PasswordResetRequest record) {
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        record.codeHash = encoder.encode(code);
        record.lastSentAt = Instant.now();
        record.expiresAt = record.lastSentAt.plusSeconds(600);
        record.sendCount++;
        record.codeVerified = false;
        mail.sendPasswordReset(record.email, code, record.token + "-" + record.sendCount);
    }
}
