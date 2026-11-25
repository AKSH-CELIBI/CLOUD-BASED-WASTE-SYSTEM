package com.example.wallet.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.example.wallet.wallet.WalletAccountRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.AfterAll;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "wallet.jwt.secret=integration-test-secret-that-is-more-than-32-bytes",
        "wallet.cors.allowed-origins=http://localhost:3000"
})
@AutoConfigureMockMvc
class UserRepositoryPostgresTest {
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

    @Autowired
    UserRepository users;

    @Autowired
    WalletAccountRepository wallets;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    MockMvc mockMvc;

    @Test
    void emailIsUniqueInPostgresql() {
        String email = "same-" + java.util.UUID.randomUUID() + "@example.com";
        users.saveAndFlush(new User(email, "bcrypt-hash"));

        assertThrows(DataIntegrityViolationException.class,
                () -> users.saveAndFlush(new User(email, "another-bcrypt-hash")));
    }

    @Test
    void unauthenticatedRequestsReturnStandard401AndCorsPreflightPasses() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/me").contextPath("/api/v1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        mockMvc.perform(options("/api/v1/accounts/me").contextPath("/api/v1")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    void openApiEndpointHasProjectMetadataAndBearerScheme() throws Exception {
        mockMvc.perform(get("/api/v1/v3/api-docs").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Digital Wallet API"))
                .andExpect(jsonPath("$.info.version").value("1.0.0"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }

    @Test
    void registrationCreatesUserAndWalletAtomically() throws Exception {
        String email = "new-" + java.util.UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register").contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"a-strong-password\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.accountNumber").value(org.hamcrest.Matchers.matchesPattern("[0-9]{12}")));

        var user = users.findByEmail(email).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(wallets.findByUserId(user.getId()).isPresent());
    }

    @Test
    void validUserCanLogInButCannotAccessAdminRoutes() throws Exception {
        String email = "user-" + java.util.UUID.randomUUID() + "@example.com";
        users.saveAndFlush(new User(email, passwordEncoder.encode("a-strong-password")));

        String token = mockMvc.perform(post("/api/v1/auth/login").contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email.toUpperCase(java.util.Locale.ROOT) + "\",\"password\":\"a-strong-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String jwt = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(token).get("token").asString();

        mockMvc.perform(get("/api/v1/admin/audit-logs").contextPath("/api/v1")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
