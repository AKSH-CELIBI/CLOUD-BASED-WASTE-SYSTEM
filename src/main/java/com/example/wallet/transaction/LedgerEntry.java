package com.example.wallet.transaction;

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
@Table(name = "ledger_entries")
public class LedgerEntry {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(optional = false) @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    private WalletTransaction transaction;
    @ManyToOne(optional = false) @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private WalletAccount account;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8, updatable = false)
    private LedgerDirection direction;
    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;
    @Column(nullable = false, length = 3, updatable = false)
    private String currency;
    @Column(name = "balance_after", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal balanceAfter;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {}
    public LedgerEntry(WalletTransaction transaction, WalletAccount account, LedgerDirection direction, BigDecimal amount,
                       BigDecimal balanceAfter) {
        this.transaction = transaction; this.account = account; this.direction = direction; this.amount = amount.setScale(4);
        this.currency = transaction.getCurrency(); this.balanceAfter = balanceAfter.setScale(4); this.createdAt = Instant.now();
    }
    public UUID getId() { return id; }
    public WalletTransaction getTransaction() { return transaction; }
    public WalletAccount getAccount() { return account; }
    public LedgerDirection getDirection() { return direction; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public Instant getCreatedAt() { return createdAt; }
}
