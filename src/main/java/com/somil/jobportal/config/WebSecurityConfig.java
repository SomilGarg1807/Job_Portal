package com.somil.jobportal.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.somil.jobportal.services.CustomUserDetailsService;

@Configuration
public class WebSecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;
    private final CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;
    @Autowired
    public WebSecurityConfig(CustomUserDetailsService customUserDetailsService, CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler) {
        this.customUserDetailsService = customUserDetailsService;
        this.customAuthenticationSuccessHandler = customAuthenticationSuccessHandler;
    }

    private final String[] publicUrl = {"/",
            "/health",
            "/health/db",
            "/api/search/**",
            "/oauth2/gmail/start",
            "/oauth2/callback/gmail",
            "/global-search/**",
            "/register",
            "/register/**",
            "/forgot-password",
            "/forgot-password/**",
            "/webjars/**",
            "/resources/**",
            "/assets/**",
            "/css/**",
            "/summernote/**",
            "/js/**",
            "/*.css",
            "/*.js",
            "/*.js.map",
            "/fonts**", "/favicon.ico", "/resources/**", "/error"};

    @Bean
    // The embedding backfill (profile "backfill") runs without a web server, where there is no HttpSecurity.
    @ConditionalOnWebApplication
    protected SecurityFilterChain securityFilterChain(HttpSecurity http,
            @org.springframework.beans.factory.annotation.Value("${app.remember-me.key:}") String rememberMeKey) throws Exception {

        http.authenticationProvider(authenticationProvider());

        http.authorizeHttpRequests(auth -> {
            auth.requestMatchers(org.springframework.http.HttpMethod.GET, "/job-details-apply/*").permitAll();
            auth.requestMatchers(publicUrl).permitAll();
            auth.anyRequest().authenticated();
        });

        http.formLogin(form->form.loginPage("/login").permitAll()
                .successHandler(customAuthenticationSuccessHandler))
                // A signed 7-day cookie lets returning users skip the slow password check entirely.
                .rememberMe(remember -> remember.key(rememberMeKey(rememberMeKey))
                        .tokenValiditySeconds((int) java.time.Duration.ofDays(7).toSeconds())
                        .userDetailsService(customUserDetailsService))
                .logout(logout-> {
                    logout.logoutUrl("/logout");
                    logout.logoutSuccessUrl("/");
                }).cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.requireCsrfProtectionMatcher(request ->
                        (request.getServletPath().startsWith("/register/")
                                || request.getServletPath().startsWith("/forgot-password")
                                || request.getServletPath().startsWith("/applications/")
                                || request.getServletPath().startsWith("/resume-comparison/"))
                                && !java.util.Set.of("GET", "HEAD", "OPTIONS", "TRACE").contains(request.getMethod())));

        return http.build();
    }

    // The key signs remember-me cookies; it must be stable across deploys or every deploy signs everyone out.
    private static String rememberMeKey(String configured) {
        if (configured != null && !configured.isBlank()) return configured;
        org.slf4j.LoggerFactory.getLogger(WebSecurityConfig.class)
                .warn("REMEMBER_ME_KEY is not set; remember-me logins will not survive a restart.");
        byte[] random = new byte[32];
        new java.security.SecureRandom().nextBytes(random);
        return java.util.HexFormat.of().formatHex(random);
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {

        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider();
        authenticationProvider.setPasswordEncoder(passwordEncoder());
        authenticationProvider.setUserDetailsService(customUserDetailsService);
        return authenticationProvider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
