package com.example.wallet.wallet;

import com.example.wallet.error.ApiException;
import com.example.wallet.identity.User;
import com.example.wallet.identity.UserRepository;
import com.example.wallet.platform.api.WalletProvisioning;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class WalletService implements WalletProvisioning {
    private static final String DEFAULT_CURRENCY = "INR";
    private final UserRepository users;
    private final WalletAccountRepository accounts;

    public WalletService(UserRepository users, WalletAccountRepository accounts) {
        this.users = users;
        this.accounts = accounts;
    }

    @Override
    @Transactional
    public String createWalletForUser(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> new IllegalStateException("Cannot provision a wallet for a missing user"));
        if (accounts.findByUserId(userId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "WALLET_ALREADY_EXISTS", "This user already has a wallet");
        }
        String number = Long.toString(accounts.nextAccountNumber());
        accounts.saveAndFlush(new WalletAccount(user, number, DEFAULT_CURRENCY));
        return number;
    }
}
