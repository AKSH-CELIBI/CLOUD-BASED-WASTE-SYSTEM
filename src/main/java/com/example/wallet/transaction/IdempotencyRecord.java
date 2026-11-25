package com.example.wallet.transaction;

import com.example.wallet.identity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys", uniqueConstraints = @UniqueConstraint(name = "uk_idempotency_user_key", columnNames = {"user_id", "idempotency_key"}))
public class IdempotencyRecord {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(optional = false) @JoinColumn(name = "user_id", nullable = false, updatable = false) private User user;
    @Column(name = "idempotency_key", nullable = false, length = 128, updatable = false) private String key;
    @Column(name = "request_hash", nullable = false, length = 64, updatable = false) private String requestHash;
    @ManyToOne(optional = false) @JoinColumn(name = "transaction_id", nullable = false, updatable = false) private WalletTransaction transaction;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected IdempotencyRecord() {}
    public IdempotencyRecord(User user, String key, String requestHash, WalletTransaction transaction) {
        this.user = user; this.key = key; this.requestHash = requestHash; this.transaction = transaction; this.createdAt = Instant.now();
    }
    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getKey() { return key; }
    public String getRequestHash() { return requestHash; }
    public WalletTransaction getTransaction() { return transaction; }
    public Instant getCreatedAt() { return createdAt; }
}
