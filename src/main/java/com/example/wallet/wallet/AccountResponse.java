package com.example.wallet.wallet;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountResponse(UUID id, String accountNumber, String balance, String currency, AccountStatus status) {
    public static AccountResponse from(WalletAccount account) {
        return new AccountResponse(account.getId(), account.getAccountNumber(), account.getBalance().setScale(4).toPlainString(),
                account.getCurrency(), account.getStatus());
    }
}
