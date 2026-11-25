package com.example.wallet.config;

import com.example.wallet.identity.User;
import com.example.wallet.identity.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final String email;
    private final String password;

    public AdminBootstrap(UserRepository users, PasswordEncoder passwords,
                          @Value("${wallet.admin.email:}") String email,
                          @Value("${wallet.admin.password:}") String password) {
        this.users = users; this.passwords = passwords; this.email = email; this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() && password.isBlank()) return;
        if (email.isBlank() || password.isBlank()) {
            throw new IllegalStateException("WALLET_ADMIN_EMAIL and WALLET_ADMIN_PASSWORD must be set together");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("WALLET_ADMIN_PASSWORD must contain 12 to 72 UTF-8 bytes");
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        User user = users.findByEmail(normalizedEmail).orElseGet(() ->
                new User(normalizedEmail, passwords.encode(password)));
        if (!passwords.matches(password, user.getPasswordHash())) {
            throw new IllegalStateException("WALLET_ADMIN_PASSWORD must match the configured admin user's current password");
        }
        user.grantAdminRole();
        users.saveAndFlush(user);
    }
}
