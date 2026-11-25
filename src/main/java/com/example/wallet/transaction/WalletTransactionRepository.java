package com.example.wallet.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {
    @Query("select distinct t from WalletTransaction t left join t.source source left join source.user sourceUser " +
            "left join t.destination destination left join destination.user destinationUser " +
            "where sourceUser.email = :email or destinationUser.email = :email")
    Page<WalletTransaction> findHistoryForUserByEmail(@Param("email") String email, Pageable pageable);
}
