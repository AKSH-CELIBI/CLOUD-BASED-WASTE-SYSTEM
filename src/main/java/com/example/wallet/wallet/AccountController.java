package com.example.wallet.wallet;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.wallet.identity.UserRepository;
import com.example.wallet.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
@Tag(name = "Accounts")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {
    private final WalletAccountRepository accounts;
    private final UserRepository users;

    public AccountController(WalletAccountRepository accounts, UserRepository users) { this.accounts = accounts; this.users = users; }

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated user's wallet")
    public AccountResponse me(Authentication authentication) {
        var user = users.findByEmail(authentication.getName())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required"));
        var account = accounts.findByUserId(user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", "Wallet account was not found"));
        return AccountResponse.from(account);
    }
}
