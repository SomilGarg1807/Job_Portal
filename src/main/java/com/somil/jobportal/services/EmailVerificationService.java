package com.somil.jobportal.services;

import com.somil.jobportal.entity.PendingRegistration;
import com.somil.jobportal.entity.Users;
import com.somil.jobportal.repository.PendingRegistrationRepository;
import com.somil.jobportal.repository.UsersTypeRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class EmailVerificationService {
    public static class InvalidCode extends RuntimeException {
        public InvalidCode(String message) { super(message); }
    }
    private final PendingRegistrationRepository pending;
    private final UsersTypeRepository types;
    private final UsersService users;
    private final PasswordEncoder encoder;
    private final VerificationMailService mail;
    private final SecureRandom random = new SecureRandom();
    public EmailVerificationService(PendingRegistrationRepository pending, UsersTypeRepository types,
                                    UsersService users, PasswordEncoder encoder, VerificationMailService mail) {
        this.pending = pending; this.types = types; this.users = users; this.encoder = encoder; this.mail = mail;
    }

    @Transactional
    public String start(Users registration) {
        String email = registration.getEmail() == null ? "" : registration.getEmail().trim().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new IllegalArgumentException("Enter a valid email address.");
        String password = registration.getPassword();
        if (password == null || password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("Use a password with at least 8 characters and at most 72 bytes.");
        int type = registration.getUserTypeId() == null ? 0 : registration.getUserTypeId().getUserTypeId();
        if ((type != 1 && type != 2) || !types.existsById(type))
            throw new IllegalArgumentException("Choose a valid account type.");
        if (users.getUserByEmail(email).isPresent())
            throw new IllegalArgumentException("Email already registered. Please sign in.");
        PendingRegistration record = pending.lockEmail(email).orElseGet(PendingRegistration::new);
        checkResend(record);
        record.email = email;
        record.token = UUID.randomUUID().toString();
        record.passwordHash = encoder.encode(password);
        record.userTypeId = type;
        record.consumed = false;
        send(record);
        pending.save(record);
        return record.token;
    }

    @Transactional
    public void resend(String token) {
        PendingRegistration record = active(token);
        checkResend(record);
        send(record);
    }

    @Transactional(noRollbackFor = InvalidCode.class)
    public void verify(String token, String code) {
        PendingRegistration record = active(token);
        if (record.expiresAt.isBefore(Instant.now())) throw new InvalidCode("This code has expired. Request a new code.");
        if (record.failedAttempts >= 5) throw new InvalidCode("Too many attempts. Try again after one hour.");
        if (code == null || !code.matches("[0-9]{6}") || !encoder.matches(code, record.codeHash)) {
            record.failedAttempts++;
            throw new InvalidCode("Incorrect verification code. " + (5 - record.failedAttempts) + " attempts remaining.");
        }
        if (users.getUserByEmail(record.email).isPresent()) throw new InvalidCode("This email is already registered. Please sign in.");
        Users account = new Users();
        account.setEmail(record.email);
        account.setPassword(record.passwordHash);
        account.setUserTypeId(types.findById(record.userTypeId).orElseThrow());
        users.addVerified(account);
        record.consumed = true;
        record.passwordHash = "consumed";
        record.codeHash = "consumed";
    }

    private PendingRegistration active(String token) {
        if (token == null) throw new InvalidCode("Start registration again to verify your email.");
        PendingRegistration record = pending.lockToken(token).orElseThrow(() -> new InvalidCode("Start registration again to verify your email."));
        if (record.consumed) throw new InvalidCode("This code has already been used. Please sign in.");
        return record;
    }
    private void checkResend(PendingRegistration record) {
        Instant now = Instant.now();
        if (record.lastSentAt != null && now.isBefore(record.lastSentAt.plusSeconds(60)))
            throw new IllegalArgumentException("Please wait 60 seconds before requesting another code.");
        if (record.windowStartedAt == null || !now.isBefore(record.windowStartedAt.plusSeconds(3600))) {
            record.windowStartedAt = now; record.sendCount = 0; record.failedAttempts = 0;
        }
        if (record.sendCount >= 5 || record.failedAttempts >= 5)
            throw new IllegalArgumentException("Verification limit reached. Please try again after one hour.");
    }
    private void send(PendingRegistration record) {
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        record.codeHash = encoder.encode(code);
        record.lastSentAt = Instant.now();
        record.expiresAt = record.lastSentAt.plusSeconds(600);
        record.sendCount++;
        mail.send(record.email, code, record.token + "-" + record.sendCount);
    }
}
