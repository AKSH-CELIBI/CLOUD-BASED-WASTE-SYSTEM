package com.example.wallet.admin;

import com.example.wallet.error.ApiException;
import com.example.wallet.identity.UserRepository;
import com.example.wallet.wallet.AccountKind;
import com.example.wallet.wallet.AccountResponse;
import com.example.wallet.wallet.AccountStatus;
import com.example.wallet.wallet.WalletAccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AccountAdminService {
    private final WalletAccountRepository accounts;
    private final UserRepository users;
    private final AccountAuditLogRepository audits;

    public AccountAdminService(WalletAccountRepository accounts, UserRepository users, AccountAuditLogRepository audits) {
        this.accounts = accounts; this.users = users; this.audits = audits;
    }

    @Transactional
    public AccountResponse setFrozen(String adminEmail, UUID accountId, boolean frozen) {
        var actor = users.findByEmail(adminEmail).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED", "Authentication is required"));
        var account = accounts.findAllForUpdateInStableOrder(java.util.List.of(accountId)).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Wallet account was not found"));
        if (account.getKind() != AccountKind.CUSTOMER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ACCOUNT", "The settlement account cannot be frozen");
        }
        AccountStatus desired = frozen ? AccountStatus.FROZEN : AccountStatus.ACTIVE;
        if (account.getStatus() != desired) {
            account.setStatus(desired);
            audits.save(new AccountAuditLog(actor, account, frozen ? "ACCOUNT_FROZEN" : "ACCOUNT_UNFROZEN"));
        }
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> auditLogs(int page, int size) {
        return audits.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))))
                .map(AuditLogResponse::from);
    }
}
