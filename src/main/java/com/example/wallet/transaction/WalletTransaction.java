package com.example.wallet.transaction;

import com.example.wallet.identity.User;
import com.example.wallet.wallet.WalletAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallet_transactions")
public class WalletTransaction {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Enumerated(EnumType.STRING) @Column(name = "transaction_type", nullable = false, length = 16, updatable = false)
    private WalletTransactionType type;
    @ManyToOne(optional = false) @JoinColumn(name = "initiated_by_user_id", nullable = false, updatable = false)
    private User initiatedBy;
    @ManyToOne @JoinColumn(name = "source_account_id", updatable = false)
    private WalletAccount source;
    @ManyToOne @JoinColumn(name = "destination_account_id", updatable = false)
    private WalletAccount destination;
    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;
    @Column(nullable = false, length = 3, updatable = false)
    private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private TransactionStatus status;
    @Column(name = "failure_code", length = 64)
    private String failureCode;
    @Column(name = "request_hash", length = 64, updatable = false)
    private String requestHash;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WalletTransaction() {}
    public WalletTransaction(WalletTransactionType type, User initiatedBy, WalletAccount source, WalletAccount destination,
                             BigDecimal amount, String currency, TransactionStatus status, String failureCode, String requestHash) {
        this.type = type; this.initiatedBy = initiatedBy; this.source = source; this.destination = destination;
        this.amount = amount.setScale(4); this.currency = currency; this.status = status; this.failureCode = failureCode;
        this.requestHash = requestHash; this.createdAt = Instant.now();
    }
    public UUID getId() { return id; }
    public WalletTransactionType getType() { return type; }
    public User getInitiatedBy() { return initiatedBy; }
    public WalletAccount getSource() { return source; }
    public WalletAccount getDestination() { return destination; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public TransactionStatus getStatus() { return status; }
    public String getFailureCode() { return failureCode; }
    public String getRequestHash() { return requestHash; }
    public Instant getCreatedAt() { return createdAt; }
}
