package com.example.wallet.identity;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class UserPasswordTest {
    @Test
    void storesBcryptHashRatherThanRawPassword() {
        var encoder = new BCryptPasswordEncoder(4);
        String rawPassword = "correct horse battery staple";
        User user = new User("user@example.com", encoder.encode(rawPassword));

        assertNotEquals(rawPassword, user.getPasswordHash());
        assertTrue(encoder.matches(rawPassword, user.getPasswordHash()));
    }
}
