package com.example.wallet.wallet;

import com.example.wallet.identity.User;
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
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallet_accounts")
public class WalletAccount {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(optional = true) @JoinColumn(name = "user_id", updatable = false)
    private User user;
    @Column(name = "account_number", length = 12, updatable = false)
    private String accountNumber;
    @Enumerated(EnumType.STRING) @Column(name = "account_kind", nullable = false, length = 16, updatable = false)
    private AccountKind kind;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;
    @Column(nullable = false, length = 3, updatable = false)
    private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private AccountStatus status;
    @Version @Column(nullable = false)
    private long version;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WalletAccount() {}

    public WalletAccount(User user, String accountNumber, String currency) {
        this.user = user; this.accountNumber = accountNumber; this.kind = AccountKind.CUSTOMER;
        this.balance = BigDecimal.ZERO.setScale(4); this.currency = currency; this.status = AccountStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public void changeBalance(BigDecimal value) { this.balance = value.setScale(4); }
    public void setStatus(AccountStatus value) { this.status = value; }
    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getAccountNumber() { return accountNumber; }
    public AccountKind getKind() { return kind; }
    public BigDecimal getBalance() { return balance; }
    public String getCurrency() { return currency; }
    public AccountStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
}
