package com.example.wallet.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findByTransactionId(UUID transactionId);
    List<LedgerEntry> findByAccountIdOrderByCreatedAtAscIdAsc(UUID accountId);
    @Query("select coalesce(sum(case when e.direction = com.example.wallet.transaction.LedgerDirection.CREDIT then e.amount else -e.amount end), 0) from LedgerEntry e where e.transaction.id = :transactionId")
    BigDecimal sumSignedEntriesForTransaction(@Param("transactionId") UUID transactionId);
}
