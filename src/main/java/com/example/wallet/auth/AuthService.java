package com.example.wallet.auth;

import com.example.wallet.error.ApiException;
import com.example.wallet.identity.User;
import com.example.wallet.identity.UserRepository;
import com.example.wallet.platform.api.WalletProvisioning;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final WalletProvisioning walletProvisioning;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager, JwtService jwtService,
            WalletProvisioning walletProvisioning) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.walletProvisioning = walletProvisioning;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Request validation failed", java.util.Map.of("password", "Must be at most 72 UTF-8 bytes"));
        }
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "An account with this email already exists");
        }
        User user;
        try {
            user = users.saveAndFlush(new User(email, passwordEncoder.encode(request.password())));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "An account with this email already exists");
        }
        String accountNumber = walletProvisioning.createWalletForUser(user.getId());
        return new RegisterResponse(jwtService.issue(user.getEmail()), accountNumber);
    }

    public AuthTokenResponse login(LoginRequest request) {
        String email = normalize(request.email());
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
        return new AuthTokenResponse(jwtService.issue(email));
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
