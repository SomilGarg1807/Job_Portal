package com.somil.jobportal.config;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Times the sign-in POST so a slow login can be attributed to a specific stage
 * instead of guessed at: total minus the user lookup is mostly password hashing.
 */
@Component
public class LoginTimingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoginTimingFilter.class);

    private static final ThreadLocal<Long> USER_LOOKUP_MILLIS = new ThreadLocal<>();

    /** Called by the user-details service so the two timings can be reported together. */
    public static void recordUserLookup(long millis) {
        USER_LOOKUP_MILLIS.set(millis);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && "/login".equals(request.getServletPath()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long totalMillis = (System.nanoTime() - start) / 1_000_000;
            Long lookupMillis = USER_LOOKUP_MILLIS.get();
            USER_LOOKUP_MILLIS.remove();
            log.info("sign-in timing: total={}ms userLookup={}ms remainder(hashing+redirect)={}ms",
                    totalMillis,
                    lookupMillis == null ? -1 : lookupMillis,
                    lookupMillis == null ? -1 : totalMillis - lookupMillis);
        }
    }
}
