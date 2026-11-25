package com.example.wallet.transaction;

import com.example.wallet.admin.AccountAdminService;
import com.example.wallet.auth.AuthService;
import com.example.wallet.auth.AuthTokenResponse;
import com.example.wallet.auth.RegisterRequest;
import com.example.wallet.identity.User;
import com.example.wallet.identity.UserRepository;
import com.example.wallet.wallet.WalletAccount;
import com.example.wallet.wallet.WalletAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.junit.jupiter.api.AfterAll;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "wallet.jwt.secret=wallet-money-engine-integration-test-secret-more-than-32-bytes",
        "spring.datasource.hikari.maximum-pool-size=30"
})
@AutoConfigureMockMvc
class WalletMoneyEnginePostgresTest {
    private static final String PASSWORD = "integration-password";
    private static final String EXTERNAL_DB_URL = System.getenv("WALLET_TEST_DATABASE_URL");
    private static final PostgreSQLContainer<?> POSTGRES = EXTERNAL_DB_URL == null || EXTERNAL_DB_URL.isBlank()
            ? new PostgreSQLContainer<>("postgres:17-alpine") : null;

    static { if (POSTGRES != null) POSTGRES.start(); }

    @AfterAll static void stopPostgres() { if (POSTGRES != null) POSTGRES.stop(); }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES == null ? EXTERNAL_DB_URL : POSTGRES.getJdbcUrl());
        registry.add("spring.datasource.username", () -> POSTGRES == null ? System.getenv("WALLET_TEST_DATABASE_USERNAME") : POSTGRES.getUsername());
        registry.add("spring.datasource.password", () -> POSTGRES == null ? System.getenv("WALLET_TEST_DATABASE_PASSWORD") : POSTGRES.getPassword());
    }

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired UserRepository users;
    @Autowired WalletAccountRepository accounts;
    @Autowired WalletTransactionRepository transactions;
    @Autowired LedgerEntryRepository ledger;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;

    @Test
    void registrationDepositReplayAndRequestHashAreConsistent() throws Exception {
        String email = email();
        MvcResult registered = mockMvc.perform(post("/api/v1/auth/register").contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumber").value(org.hamcrest.Matchers.matchesPattern("[0-9]{12}")))
                .andReturn();
        String token = json.readTree(registered.getResponse().getContentAsString()).get("token").asString();
        Fixture owner = new Fixture(email, token, json.readTree(registered.getResponse().getContentAsString())
                .get("accountNumber").asString());
        assertAccount(owner, "0.0000");

        MvcResult first = deposit(owner, "50.75", "deposit-once");
        assertEquals(201, first.getResponse().getStatus());
        String original = first.getResponse().getContentAsString();
        MvcResult replay = deposit(owner, "50.7500", "deposit-once");
        assertEquals(200, replay.getResponse().getStatus());
        assertEquals(json.readTree(original), json.readTree(replay.getResponse().getContentAsString()));
        assertEquals(422, deposit(owner, "51", "deposit-once").getResponse().getStatus());
        assertAccount(owner, "50.7500");

        var completed = transactions.findAll().stream().filter(t -> t.getInitiatedBy().getEmail().equals(email)
                && t.getStatus() == TransactionStatus.COMPLETED).toList();
        assertEquals(1, completed.size());
        assertEquals(BigDecimal.ZERO.setScale(4), ledger.sumSignedEntriesForTransaction(completed.getFirst().getId()).setScale(4));
        assertEquals(2, ledger.findByTransactionId(completed.getFirst().getId()).size());
        assertTrue(transactions.findAll().stream().anyMatch(t -> t.getInitiatedBy().getEmail().equals(email)
                && t.getStatus() == TransactionStatus.FAILED));
        assertThrows(DataAccessException.class, () -> jdbc.update("DELETE FROM ledger_entries WHERE transaction_id = ?",
                completed.getFirst().getId()));
    }

    @Test
    void transferUsesAuthenticatedWalletAndHistoryIsScopedToEachOwner() throws Exception {
        Fixture alice = register();
        Fixture bob = register();
        assertEquals(201, deposit(alice, "80", "alice-fund").getResponse().getStatus());
        MvcResult sent = transfer(alice, bob.accountNumber(), "25.25", "alice-to-bob");
        assertEquals(201, sent.getResponse().getStatus());
        assertAccount(alice, "54.7500");
        assertAccount(bob, "25.2500");
        mockMvc.perform(get("/api/v1/transactions/history").contextPath("/api/v1")
                        .header("Authorization", bearer(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].type").value("TRANSFER"));
        Fixture stranger = register();
        mockMvc.perform(get("/api/v1/transactions/history").contextPath("/api/v1")
                        .header("Authorization", bearer(stranger)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void insufficientFundsAreRecordedAndAdminFreezeIsAuditedAndEnforced() throws Exception {
        Fixture alice = register();
        Fixture bob = register();
        assertEquals(422, transfer(alice, bob.accountNumber(), "1", "no-funds").getResponse().getStatus());
        assertTrue(transactions.findAll().stream().anyMatch(t -> t.getStatus() == TransactionStatus.FAILED
                && "INSUFFICIENT_FUNDS".equals(t.getFailureCode())));

        User admin = users.saveAndFlush(new User(email(), passwordEncoder.encode(PASSWORD)));
        admin.grantAdminRole();
        users.saveAndFlush(admin);
        String adminToken = authService.login(new com.example.wallet.auth.LoginRequest(admin.getEmail(), PASSWORD)).token();
        WalletAccount target = accounts.findByAccountNumber(bob.accountNumber()).orElseThrow();
        assertEquals(201, deposit(alice, "5", "fund-before-freeze").getResponse().getStatus());
        mockMvc.perform(patch("/api/v1/admin/accounts/{id}/freeze", target.getId()).contextPath("/api/v1")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"frozen\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FROZEN"));
        assertEquals(422, deposit(bob, "2", "frozen-deposit").getResponse().getStatus());
        assertEquals(422, transfer(alice, bob.accountNumber(), "1", "frozen-destination").getResponse().getStatus());
        assertEquals(422, transfer(bob, alice.accountNumber(), "1", "frozen-source").getResponse().getStatus());
        mockMvc.perform(get("/api/v1/admin/audit-logs").contextPath("/api/v1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("ACCOUNT_FROZEN"));
        mockMvc.perform(patch("/api/v1/admin/accounts/{id}/freeze", target.getId()).contextPath("/api/v1")
                        .header("Authorization", "Bearer " + alice.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"frozen\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void twentyConcurrentTransfersPreserveExactBalancesAndLedgerInvariants() throws Exception {
        Fixture alice = register();
        Fixture bob = register();
        assertEquals(201, deposit(alice, "20", "concurrency-fund").getResponse().getStatus());
        List<CompletableFuture<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String key = "parallel-" + i;
            futures.add(CompletableFuture.supplyAsync(() -> {
                try { return transfer(alice, bob.accountNumber(), "1", key).getResponse().getStatus(); }
                catch (Exception exception) { throw new RuntimeException(exception); }
            }));
        }
        for (CompletableFuture<Integer> future : futures) {
            int responseStatus = future.get(30, TimeUnit.SECONDS);
            assertEquals(201, responseStatus, "A concurrent transfer failed; inspect application logs for its exception");
        }
        assertAccount(alice, "0.0000");
        assertAccount(bob, "20.0000");
        assertLedgerInvariant(accounts.findByAccountNumber(alice.accountNumber()).orElseThrow());
        assertLedgerInvariant(accounts.findByAccountNumber(bob.accountNumber()).orElseThrow());
        for (WalletTransaction transaction : transactions.findAll()) {
            if (transaction.getStatus() == TransactionStatus.COMPLETED) {
                assertEquals(BigDecimal.ZERO.setScale(4), ledger.sumSignedEntriesForTransaction(transaction.getId()).setScale(4));
            }
        }
    }

    @Test
    void oppositeDirectionTransfersCompleteWithoutDeadlock() throws Exception {
        Fixture alice = register();
        Fixture bob = register();
        deposit(alice, "10", "reverse-fund-alice");
        deposit(bob, "10", "reverse-fund-bob");
        var aToB = CompletableFuture.supplyAsync(() -> performTransfer(alice, bob.accountNumber(), "5", "ab"));
        var bToA = CompletableFuture.supplyAsync(() -> performTransfer(bob, alice.accountNumber(), "3", "ba"));
        assertEquals(201, aToB.get(15, TimeUnit.SECONDS), "A to B response failed");
        assertEquals(201, bToA.get(15, TimeUnit.SECONDS), "B to A response failed");
        assertAccount(alice, "8.0000");
        assertAccount(bob, "12.0000");
    }

    @Test
    void concurrentRetriesWithOneIdempotencyKeyMoveFundsOnlyOnce() throws Exception {
        Fixture alice = register();
        Fixture bob = register();
        deposit(alice, "10", "same-key-fund");
        List<CompletableFuture<MvcResult>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try { return transfer(alice, bob.accountNumber(), "2", "same-key-transfer"); }
                catch (Exception exception) { throw new RuntimeException(exception); }
            }));
        }
        List<MvcResult> results = new ArrayList<>();
        for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
        assertEquals(1, results.stream().filter(r -> r.getResponse().getStatus() == 201).count());
        assertEquals(19, results.stream().filter(r -> r.getResponse().getStatus() == 200).count(),
                () -> "Unexpected concurrent retry responses: " + results.stream()
                        .map(r -> r.getResponse().getStatus() + " "
                                + new String(r.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8))
                        .collect(java.util.stream.Collectors.joining(" | ")));
        String txId = json.readTree(results.getFirst().getResponse().getContentAsString()).get("id").asString();
        assertTrue(results.stream().allMatch(r -> {
            try { return txId.equals(json.readTree(r.getResponse().getContentAsString()).get("id").asString()); }
            catch (Exception exception) { throw new RuntimeException(exception); }
        }));
        assertAccount(alice, "8.0000");
        assertAccount(bob, "2.0000");
        assertEquals(2, ledger.findByTransactionId(UUID.fromString(txId)).size());
    }

    private Fixture register() {
        String email = email();
        var response = authService.register(new RegisterRequest(email, PASSWORD));
        return new Fixture(email, response.token(), response.accountNumber());
    }

    private MvcResult deposit(Fixture fixture, String amount, String key) throws Exception {
        return mockMvc.perform(post("/api/v1/transactions/deposit").contextPath("/api/v1")
                        .header("Authorization", bearer(fixture)).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"" + amount + "\"}"))
                .andReturn();
    }

    private MvcResult transfer(Fixture fixture, String destination, String amount, String key) throws Exception {
        return mockMvc.perform(post("/api/v1/transactions/transfer").contextPath("/api/v1")
                        .header("Authorization", bearer(fixture)).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinationAccountNumber\":\"" + destination + "\",\"amount\":\"" + amount + "\"}"))
                .andReturn();
    }

    private int performTransfer(Fixture fixture, String destination, String amount, String key) {
        try { return transfer(fixture, destination, amount, key).getResponse().getStatus(); }
        catch (Exception exception) { throw new RuntimeException(exception); }
    }

    private void assertAccount(Fixture fixture, String expectedBalance) throws Exception {
        mockMvc.perform(get("/api/v1/accounts/me").contextPath("/api/v1")
                        .header("Authorization", bearer(fixture)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value(fixture.accountNumber()))
                .andExpect(jsonPath("$.balance").value(expectedBalance))
                .andExpect(jsonPath("$.currency").value("INR"));
    }

    private void assertLedgerInvariant(WalletAccount account) {
        List<LedgerEntry> entries = ledger.findByAccountIdOrderByCreatedAtAscIdAsc(account.getId());
        assertFalse(entries.isEmpty());
        assertEquals(account.getBalance().setScale(4), entries.getLast().getBalanceAfter().setScale(4));
        for (LedgerEntry entry : entries) assertTrue(entry.getAmount().signum() > 0);
    }

    private static String bearer(Fixture fixture) { return "Bearer " + fixture.token(); }
    private static String email() { return "wallet-" + UUID.randomUUID() + "@example.com"; }
    private record Fixture(String email, String token, String accountNumber) {}
}
