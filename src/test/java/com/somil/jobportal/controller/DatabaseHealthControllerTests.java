package com.somil.jobportal.controller;

import java.time.Duration;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DatabaseHealthControllerTests {

    @ParameterizedTest
    @CsvSource({"true,true", "true,false", "false,true", "false,false"})
    void databaseDeterminesHttpStatusWhileRedisKeepaliveIsBestEffort(boolean databaseUp, boolean redisUp)
            throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        if (databaseUp) {
            when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        } else {
            when(jdbc.queryForObject("SELECT 1", Integer.class))
                    .thenThrow(new DataAccessResourceFailureException("Database unavailable"));
        }
        if (!redisUp) {
            doThrow(new RedisConnectionFailureException("Redis unavailable"))
                    .when(values).set("jobportal:keepalive", "active", Duration.ofMinutes(10));
        }

        var mvc = MockMvcBuilders.standaloneSetup(new DatabaseHealthController(jdbc, redis)).build();
        var response = mvc.perform(get("/health/db"))
                .andExpect(status().is(databaseUp ? 200 : 503))
                .andExpect(jsonPath("$.status").value(databaseUp ? "UP" : "DOWN"))
                .andExpect(jsonPath("$.database").value(databaseUp ? "UP" : "DOWN"))
                .andExpect(jsonPath("$.redis").value(redisUp ? "UP" : "DOWN"))
                .andExpect(header().string("Cache-Control", "no-store"));
        if (databaseUp) {
            response.andExpect(jsonPath("$.latencyMs").isNumber());
        }
        verify(values).set("jobportal:keepalive", "active", Duration.ofMinutes(10));
        verify(jdbc).queryForObject("SELECT 1", Integer.class);
    }
}
