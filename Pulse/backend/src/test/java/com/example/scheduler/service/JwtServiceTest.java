package com.example.scheduler.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", "test-secret-key-for-jwt-signing-must-be-long-enough-0123456789");
        ReflectionTestUtils.setField(jwtService, "expirationMs", 3600000L);
    }

    private UserDetails admin() {
        return new User("admin", "irrelevant", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    void generatedTokenContainsCorrectUsername() {
        String token = jwtService.generateToken(admin());
        assertEquals("admin", jwtService.extractUsername(token));
    }

    @Test
    void tokenIsValidForMatchingUser() {
        String token = jwtService.generateToken(admin());
        assertTrue(jwtService.isTokenValid(token, admin()));
    }

    @Test
    void tokenIsInvalidForDifferentUser() {
        String token = jwtService.generateToken(admin());
        UserDetails other = new User("someone-else", "irrelevant", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertFalse(jwtService.isTokenValid(token, other));
    }

    @Test
    void malformedTokenIsRejected() {
        assertThrows(Exception.class, () -> jwtService.extractUsername("not-a-real-token"));
    }
}
