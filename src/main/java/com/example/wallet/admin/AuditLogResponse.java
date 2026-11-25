package com.example.wallet.admin;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(UUID id, String adminEmail, UUID accountId, String accountNumber,
                               String action, Instant createdAt) {
    public static AuditLogResponse from(AccountAuditLog audit) {
        return new AuditLogResponse(audit.getId(), audit.getActor().getEmail(), audit.getAccount().getId(),
                audit.getAccount().getAccountNumber(), audit.getAction(), audit.getCreatedAt());
    }
}
