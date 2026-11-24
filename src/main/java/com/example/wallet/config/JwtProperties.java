package com.example.wallet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;

@Validated
@ConfigurationProperties(prefix = "wallet.jwt")
public record JwtProperties(String secret, long expirationSeconds) {
    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("WALLET_JWT_SECRET must contain at least 32 UTF-8 bytes");
        }
        if (expirationSeconds < 60) {
            throw new IllegalStateException("wallet.jwt.expiration-seconds must be at least 60");
        }
    }
}
