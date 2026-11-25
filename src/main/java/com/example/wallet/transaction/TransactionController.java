package com.example.wallet.transaction;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/transactions")
@Tag(name = "Transactions")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {
    private final WalletTransactionService transactions;
    private final WalletTransactionRepository transactionRepository;

    public TransactionController(WalletTransactionService transactions, WalletTransactionRepository transactionRepository) {
        this.transactions = transactions; this.transactionRepository = transactionRepository;
    }

    @PostMapping("/deposit")
    @Operation(summary = "Deposit funds into the authenticated wallet")
    public ResponseEntity<TransactionResponse> deposit(Authentication authentication,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody DepositRequest request) {
        var result = transactions.deposit(authentication.getName(), key, request.amount());
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(TransactionResponse.from(result.transaction()));
    }

    @PostMapping("/transfer")
    @Operation(summary = "Transfer funds to another wallet")
    public ResponseEntity<TransactionResponse> transfer(Authentication authentication,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody TransferRequest request) {
        var result = transactions.transfer(authentication.getName(), key,
                request.destinationAccountNumber(), request.amount());
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(TransactionResponse.from(result.transaction()));
    }

    @GetMapping("/history")
    @Operation(summary = "Get the authenticated wallet's transaction history")
    public PageResponse<TransactionResponse> history(Authentication authentication,
            @Parameter(description = "Zero-based page number") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, maximum 100") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.from(transactionRepository.findHistoryForUserByEmail(authentication.getName(), pageable)
                .map(TransactionResponse::from));
    }
}
