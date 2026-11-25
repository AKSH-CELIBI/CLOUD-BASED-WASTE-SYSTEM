package com.example.wallet.admin;

import com.example.wallet.identity.User;
import com.example.wallet.wallet.WalletAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_audit_logs")
public class AccountAuditLog {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(optional = false) @JoinColumn(name = "actor_user_id", nullable = false, updatable = false) private User actor;
    @ManyToOne(optional = false) @JoinColumn(name = "account_id", nullable = false, updatable = false) private WalletAccount account;
    @Column(nullable = false, length = 32, updatable = false) private String action;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected AccountAuditLog() {}
    public AccountAuditLog(User actor, WalletAccount account, String action) {
        this.actor = actor; this.account = account; this.action = action; this.createdAt = Instant.now();
    }
    public UUID getId() { return id; }
    public User getActor() { return actor; }
    public WalletAccount getAccount() { return account; }
    public String getAction() { return action; }
    public Instant getCreatedAt() { return createdAt; }
}
