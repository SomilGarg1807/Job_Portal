package com.somil.jobportal.services;

import com.somil.jobportal.entity.Users;
import com.somil.jobportal.entity.UsersType;
import com.somil.jobportal.repository.PasswordResetRequestRepository;
import com.somil.jobportal.repository.UsersRepository;
import com.somil.jobportal.repository.UsersTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:password-reset;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
class PasswordResetServiceTests {
    @Autowired PasswordResetService passwordReset;
    @Autowired PasswordResetRequestRepository resets;
    @Autowired UsersRepository users;
    @Autowired UsersTypeRepository types;
    @Autowired PasswordEncoder encoder;
    @Autowired EmailVerificationService verification;
    @Autowired MockMvc mvc;
    @MockitoBean VerificationMailService mail;
    AtomicReference<String> sentCode = new AtomicReference<>();

    @BeforeEach
    void setup() {
        if (types.count() == 0) {
            types.save(new UsersType(0, "Recruiter", null));
            types.save(new UsersType(0, "Job Seeker", null));
        }
        doAnswer(call -> {
            sentCode.set(call.getArgument(1));
            return null;
        }).when(mail).send(anyString(), anyString(), anyString());
        doAnswer(call -> {
            sentCode.set(call.getArgument(1));
            return null;
        }).when(mail).sendPasswordReset(anyString(), anyString(), anyString());
    }

    private Users registerUser(int typeId, String rawPassword) {
        Users account = new Users();
        account.setEmail(UUID.randomUUID() + "@example.com");
        account.setPassword(rawPassword);
        account.setUserTypeId(types.findById(typeId).orElseThrow());
        String token = verification.start(account);
        verification.verify(token, sentCode.get());
        return users.findByEmail(account.getEmail()).orElseThrow();
    }

    @Test
    void unknownEmailDoesNotSendMail() {
        assertThat(passwordReset.start("missing-" + UUID.randomUUID() + "@example.com")).isEmpty();
        verify(mail, never()).sendPasswordReset(anyString(), anyString(), anyString());
    }

    @Test
    @Transactional
    void seekerCanResetPasswordWithEmailCode() {
        Users account = registerUser(2, "OldPassword123!");
        String token = passwordReset.start(account.getEmail());
        assertThat(token).isNotBlank();
        assertThat(sentCode.get()).matches("\\d{6}");
        passwordReset.verify(token, sentCode.get());
        passwordReset.resetPassword(token, "BrandNewPass99!");
        Users updated = users.findByEmail(account.getEmail()).orElseThrow();
        assertThat(encoder.matches("BrandNewPass99!", updated.getPassword())).isTrue();
        assertThatThrownBy(() -> passwordReset.verify(token, sentCode.get()))
                .isInstanceOf(PasswordResetService.InvalidCode.class);
    }

    @Test
    void recruiterCanResetPasswordThroughHttpFlow() throws Exception {
        Users account = registerUser(1, "RecruiterPass123!");

        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/forgot-password").servletPath("/forgot-password").with(csrf()).session(session)
                        .param("email", account.getEmail()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/forgot-password/verify"));

        mvc.perform(post("/forgot-password/verify").with(csrf()).session(session).param("code", sentCode.get()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/forgot-password/new"));

        mvc.perform(post("/forgot-password/new").with(csrf()).session(session)
                        .param("password", "FreshRecruiter88!")
                        .param("confirmPassword", "FreshRecruiter88!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?reset=1"));

        assertThat(encoder.matches("FreshRecruiter88!", users.findByEmail(account.getEmail()).orElseThrow().getPassword())).isTrue();
        mvc.perform(get("/login")).andExpect(status().isOk());
    }
}
