package com.nomion.setup;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "nomion.initial-setup.enabled=true",
        "nomion.initial-setup.token=0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
class InitialSetupIntegrationTest {


    private static final String SETUP_TOKEN =
            "0123456789abcdef0123456789abcdef";


    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;


    @BeforeEach
    void prepareDatabase() {
        cleanupTestData();
    }


    @AfterEach
    void cleanupDatabase() {
        cleanupTestData();
    }


    @Test
    void shouldInitializeOnceAndAuthenticateAdministrator() throws Exception {

        String slug = "setup-" + UUID.randomUUID();
        String email = "setup-" + UUID.randomUUID() + "@nomion.com";
        String request = setupRequest(slug, email);

        mockMvc.perform(post("/setup/initialize")
                        .header("X-Setup-Token", SETUP_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenant.slug").value(slug))
                .andExpect(jsonPath("$.administrator.email").value(email))
                .andExpect(jsonPath("$.administrator.memberships[0].tenantId").exists())
                .andExpect(jsonPath("$.initializedAt").exists());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "secure-password"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"))
                .andExpect(jsonPath("$.user.id").exists())
                .andExpect(jsonPath("$.user.name").value("Administrador Nomion"))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.tokens.roles", hasItems(
                        "PLATFORM_ADMIN",
                        "TENANT_ADMIN"
                )))
                .andExpect(jsonPath("$.tokens.permissions", hasItems(
                        "TENANT_CREATE",
                        "TENANT_READ",
                        "USER_CREATE",
                        "ROLE_ASSIGN"
                )));

        mockMvc.perform(post("/setup/initialize")
                        .header("X-Setup-Token", SETUP_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        "Setup inicial não disponível"
                ));
    }


    @Test
    void shouldRejectInvalidSetupToken() throws Exception {

        mockMvc.perform(post("/setup/initialize")
                        .header("X-Setup-Token", "invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(setupRequest(
                                "setup-" + UUID.randomUUID(),
                                "setup-" + UUID.randomUUID() + "@nomion.com"
                        )))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(
                        "Credencial de setup inválida"
                ));
    }


    @Test
    void shouldAllowOnlyOneConcurrentInitialization() throws Exception {

        String request = setupRequest(
                "setup-" + UUID.randomUUID(),
                "setup-" + UUID.randomUUID() + "@nomion.com"
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> initialize = () -> {
            ready.countDown();
            start.await();

            return mockMvc.perform(post("/setup/initialize")
                            .header("X-Setup-Token", SETUP_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Integer> first = executor.submit(initialize);
        Future<Integer> second = executor.submit(initialize);

        try {
            boolean workersReady = ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            assertTrue(workersReady);

            List<Integer> statuses = List.of(
                            first.get(20, TimeUnit.SECONDS),
                            second.get(20, TimeUnit.SECONDS)
                    )
                    .stream()
                    .sorted()
                    .toList();

            assertEquals(List.of(201, 404), statuses);
        } finally {
            start.countDown();
            first.cancel(true);
            second.cancel(true);
            executor.shutdownNow();
        }
    }


    @Test
    void shouldRollbackAllDataWhenRoleAssignmentFails() throws Exception {

        String slug = "setup-" + UUID.randomUUID();
        String email = "setup-" + UUID.randomUUID() + "@nomion.com";

        jdbcTemplate.update("""
                UPDATE nomion_test.roles
                SET active = FALSE
                WHERE code = 'PLATFORM_ADMIN'
                  AND scope = 'PLATFORM'
                """);

        mockMvc.perform(post("/setup/initialize")
                        .header("X-Setup-Token", SETUP_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(setupRequest(slug, email)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Perfil de plataforma inválido"
                ));

        assertEquals(0, countByValue(
                "SELECT COUNT(*) FROM nomion_test.tenants WHERE slug = ?",
                slug
        ));
        assertEquals(0, countByValue(
                "SELECT COUNT(*) FROM nomion_test.users WHERE email = ?",
                email
        ));
        assertTrue(Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                """
                        SELECT initialized_at IS NULL
                        FROM nomion_test.initial_setup_state
                        WHERE id = 1
                        """,
                Boolean.class
        )));
    }


    private String setupRequest(String slug, String email) {

        return """
                {
                  "tenant": {
                    "name": "Tenant Inicial",
                    "slug": "%s"
                  },
                  "administrator": {
                    "name": "Administrador Nomion",
                    "email": "%s",
                    "password": "secure-password"
                  }
                }
                """.formatted(slug, email);
    }


    private int countByValue(String sql, String value) {

        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                value
        );

        return count == null ? 0 : count;
    }


    private void cleanupTestData() {

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.update("""
                    DELETE FROM nomion_test.refresh_tokens
                    WHERE membership_id IN (
                        SELECT membership.id
                        FROM nomion_test.tenant_memberships membership
                        INNER JOIN nomion_test.users app_user
                            ON app_user.id = membership.user_id
                        WHERE app_user.email LIKE 'setup-%@nomion.com'
                    )
                    """);
            jdbcTemplate.update("""
                    DELETE FROM nomion_test.membership_role_assignments
                    WHERE membership_id IN (
                        SELECT membership.id
                        FROM nomion_test.tenant_memberships membership
                        INNER JOIN nomion_test.users app_user
                            ON app_user.id = membership.user_id
                        WHERE app_user.email LIKE 'setup-%@nomion.com'
                    )
                    """);
            jdbcTemplate.update("""
                    DELETE FROM nomion_test.user_platform_role_assignments
                    WHERE user_id IN (
                        SELECT id
                        FROM nomion_test.users
                        WHERE email LIKE 'setup-%@nomion.com'
                    )
                    """);
            jdbcTemplate.update("""
                    UPDATE nomion_test.initial_setup_state
                    SET initialized_at = NULL,
                        initialized_by_user_id = NULL,
                        version = version + 1
                    WHERE id = 1
                    """);
            jdbcTemplate.update("""
                    DELETE FROM nomion_test.tenant_memberships
                    WHERE user_id IN (
                        SELECT id
                        FROM nomion_test.users
                        WHERE email LIKE 'setup-%@nomion.com'
                    )
                    """);
            jdbcTemplate.update("""
                    DELETE FROM nomion_test.users
                    WHERE email LIKE 'setup-%@nomion.com'
                    """);
            jdbcTemplate.update("""
                    DELETE FROM nomion_test.tenants
                    WHERE slug LIKE 'setup-%'
                    """);
            jdbcTemplate.update("""
                    UPDATE nomion_test.roles
                    SET active = TRUE
                    WHERE code = 'PLATFORM_ADMIN'
                      AND scope = 'PLATFORM'
                    """);
        });
    }
}
