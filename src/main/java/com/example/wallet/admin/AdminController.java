package com.example.wallet.admin;

import com.example.wallet.transaction.PageResponse;
import com.example.wallet.wallet.AccountResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/admin")
@Tag(name = "Admin")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {
    private final AccountAdminService admin;

    public AdminController(AccountAdminService admin) { this.admin = admin; }

    @PatchMapping("/accounts/{id}/freeze")
    @Operation(summary = "Freeze or unfreeze a customer wallet")
    public AccountResponse freeze(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody FreezeRequest request) {
        return admin.setFrozen(authentication.getName(), id, request.frozen());
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Get paged wallet administration audit logs")
    public PageResponse<AuditLogResponse> auditLogs(
            @Parameter(description = "Zero-based page number") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, maximum 100") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(admin.auditLogs(page, size));
    }
}
