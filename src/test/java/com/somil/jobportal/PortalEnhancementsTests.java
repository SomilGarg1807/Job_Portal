package com.somil.jobportal;

import com.somil.jobportal.entity.*;
import com.somil.jobportal.repository.*;
import com.somil.jobportal.services.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:enhancements;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
class PortalEnhancementsTests {
    @Autowired EmailVerificationService verification;
    @Autowired PendingRegistrationRepository pending;
    @Autowired UsersRepository users;
    @Autowired UsersTypeRepository types;
    @Autowired PasswordEncoder encoder;
    @Autowired MockMvc mvc;
    @MockitoBean VerificationMailService mail;
    AtomicReference<String> sentCode = new AtomicReference<>();

    @BeforeEach void setup() {
        if (types.count() == 0) {
            types.save(new UsersType(0, "Recruiter", null));
            types.save(new UsersType(0, "Job Seeker", null));
        }
        doAnswer(call -> { sentCode.set(call.getArgument(1)); return null; }).when(mail).send(anyString(), anyString(), anyString());
    }
    Users registration() {
        Users account = new Users();
        account.setEmail(UUID.randomUUID() + "@example.com");
        account.setPassword("ValidPassword123!");
        account.setUserTypeId(types.findById(2).orElseThrow());
        return account;
    }
    void age(String email) {
        var record = pending.findById(email).orElseThrow();
        record.lastSentAt = Instant.now().minusSeconds(61);
        pending.save(record);
    }
    @Test void accountExistsOnlyAfterVerificationAndCodeCannotBeReused() {
        Users account = registration();
        String token = verification.start(account);
        assertThat(users.findByEmail(account.getEmail())).isEmpty();
        var stored = pending.findById(account.getEmail()).orElseThrow();
        assertThat(stored.codeHash).isNotEqualTo(sentCode.get());
        assertThat(stored.passwordHash).isNotEqualTo(account.getPassword());
        verification.verify(token, sentCode.get());
        var created = users.findByEmail(account.getEmail()).orElseThrow();
        assertThat(encoder.matches(account.getPassword(), created.getPassword())).isTrue();
        assertThatThrownBy(() -> verification.verify(token, sentCode.get())).hasMessageContaining("already been used");
    }
    @Test void wrongAttemptsPersistAcrossTransactionsAndResendsDoNotBypassLimit() {
        Users account = registration();
        String token = verification.start(account);
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> verification.verify(token, "invalid")).isInstanceOf(EmailVerificationService.InvalidCode.class);
        }
        assertThat(pending.findById(account.getEmail()).orElseThrow().failedAttempts).isEqualTo(5);
        assertThatThrownBy(() -> verification.verify(token, sentCode.get())).hasMessageContaining("Too many attempts");
        age(account.getEmail());
        assertThatThrownBy(() -> verification.resend(token)).hasMessageContaining("limit reached");
        assertThatThrownBy(() -> verification.start(account)).hasMessageContaining("limit reached");
        assertThat(users.findByEmail(account.getEmail())).isEmpty();
    }
    @Test void expiredCodesAndImmediateResendsAreRejected() {
        Users account = registration();
        String token = verification.start(account);
        assertThatThrownBy(() -> verification.resend(token)).hasMessageContaining("60 seconds");
        var record = pending.findById(account.getEmail()).orElseThrow();
        record.expiresAt = Instant.now().minusSeconds(1);
        pending.save(record);
        assertThatThrownBy(() -> verification.verify(token, sentCode.get())).hasMessageContaining("expired");
        age(account.getEmail());
        verification.resend(token);
        verification.verify(token, sentCode.get());
        assertThat(users.findByEmail(account.getEmail())).isPresent();
    }
    @Test void deliveryFailureCreatesNeitherAccountNorPendingRecord() {
        Users account = registration();
        doThrow(new IllegalStateException("Mail unavailable")).when(mail).send(anyString(), anyString(), anyString());
        assertThatThrownBy(() -> verification.start(account)).hasMessage("Mail unavailable");
        assertThat(pending.findById(account.getEmail())).isEmpty();
        assertThat(users.findByEmail(account.getEmail())).isEmpty();
    }
    @Test void signupRequiresCsrfAndSessionBoundCode() throws Exception {
        mvc.perform(post("/register/new").servletPath("/register/new")).andExpect(status().isForbidden());
        Users account = registration();
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/register/new").servletPath("/register/new").with(csrf()).session(session)
                .param("email", account.getEmail()).param("password", account.getPassword()).param("userTypeId", "2"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/register/verify"));
        mvc.perform(post("/register/verify").with(csrf()).param("code", sentCode.get()))
                .andExpect(model().attributeExists("error"));
        mvc.perform(post("/register/verify").with(csrf()).session(session).param("code", sentCode.get()))
                .andExpect(model().attribute("verified", true));
    }
    @Test void publicSuggestionsAcceptEmptyInputAndTemplatesRender() throws Exception {
        mvc.perform(get("/api/search/jobs").param("q", "x")).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/search/jobs").param("q", "Java")).andExpect(status().isOk());
        mvc.perform(get("/api/search/locations").param("q", "x")).andExpect(status().isOk()).andExpect(content().json("[]"));
        preview("home", mvc.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        preview("search", mvc.perform(get("/global-search/")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var session = new MockHttpSession();
        session.setAttribute("emailVerificationToken", "preview");
        preview("verify", mvc.perform(get("/register/verify").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    @Test void recruiterCannotSaveMissingCompany() throws Exception {
        Users account = registration();
        account.setUserTypeId(types.findById(1).orElseThrow());
        String token = verification.start(account);
        verification.verify(token, sentCode.get());
        var request = multipart("/recruiter-profile/addNew").file("image", new byte[0])
                .with(user(account.getEmail()).authorities(() -> "Recruiter"))
                .param("firstName", "Alex").param("lastName", "Taylor").param("company", "   ")
                .param("city", "Pune").param("state", "Maharashtra").param("country", "India");
        mvc.perform(request).andExpect(view().name("recruiter_profile")).andExpect(model().attributeExists("error"));
        preview("recruiter-profile", mvc.perform(get("/recruiter-profile/").with(user(account.getEmail()).authorities(() -> "Recruiter")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Users seeker = registration();
        verification.verify(verification.start(seeker), sentCode.get());
        preview("seeker-profile", mvc.perform(get("/job-seeker-profile/").with(user(seeker.getEmail()).authorities(() -> "Job Seeker")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    static void preview(String name, String html) throws Exception {
        if (!Boolean.getBoolean("dashboard.preview")) return;
        Path directory = Path.of("target/dashboard-preview");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".html"), html);
    }
}
