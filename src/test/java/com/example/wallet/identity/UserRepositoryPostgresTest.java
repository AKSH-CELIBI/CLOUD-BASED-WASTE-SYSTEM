package com.example.wallet.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
@Testcontainers(disabledWithoutDocker = true)
class UserRepositoryPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    MockMvc mockMvc;

    @BeforeEach
    void clearUsers() {
        users.deleteAll();
    }

    @Test
    void emailIsUniqueInPostgresql() {
        users.saveAndFlush(new User("same@example.com", "bcrypt-hash"));

        assertThrows(DataIntegrityViolationException.class,
                () -> users.saveAndFlush(new User("same@example.com", "another-bcrypt-hash")));
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
    void registrationRollsBackWhenWalletProvisioningIsUnavailable() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\",\"password\":\"a-strong-password\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("WALLET_PROVISIONING_UNAVAILABLE"));

        org.junit.jupiter.api.Assertions.assertFalse(users.existsByEmail("new@example.com"));
    }

    @Test
    void validUserCanLogInButCannotAccessAdminRoutes() throws Exception {
        users.saveAndFlush(new User("user@example.com", passwordEncoder.encode("a-strong-password")));

        String token = mockMvc.perform(post("/api/v1/auth/login").contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"USER@example.com\",\"password\":\"a-strong-password\"}"))
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
