package com.example.wallet.wallet;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletAccountRepository extends JpaRepository<WalletAccount, UUID> {
    Optional<WalletAccount> findByUserId(UUID userId);
    Optional<WalletAccount> findByAccountNumber(String accountNumber);
    Optional<WalletAccount> findByKind(AccountKind kind);
    @Query(value = "select nextval('wallet_account_number_seq')", nativeQuery = true)
    long nextAccountNumber();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from WalletAccount a where a.id in :ids order by a.id asc")
    List<WalletAccount> findAllForUpdateInStableOrder(@Param("ids") Collection<UUID> ids);
}
