package com.nomion.publicentity;

import com.nomion.publicentity.domain.PublicEntity;
import com.nomion.publicentity.domain.PublicEntityType;
import com.nomion.publicentity.repository.PublicEntityRepository;
import com.nomion.security.AuthorizationContext;
import com.nomion.security.JwtService;
import com.nomion.tenant.domain.Tenant;
import com.nomion.tenant.domain.TenantMembership;
import com.nomion.tenant.repository.TenantRepository;
import com.nomion.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PublicEntitySecurityIntegrationTest {


    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PublicEntityRepository publicEntityRepository;


    @Test
    @Transactional
    void shouldCreateAndNormalizeEntityWithPermission() throws Exception {

        Tenant tenant = saveTenant("create");
        String accessToken = accessToken(
                tenant.getId(),
                Set.of("CONSULTANT"),
                Set.of("PUBLIC_ENTITY_CREATE")
        );

        mockMvc.perform(post("/public-entities")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest("12.ABC.345/01DE-35")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Prefeitura Municipal"))
                .andExpect(jsonPath("$.cnpj").value("12ABC34501DE35"))
                .andExpect(jsonPath("$.state").value("SP"))
                .andExpect(jsonPath("$.active").value(true));
    }


    @Test
    @Transactional
    void shouldListOnlyEntitiesFromActiveTenant() throws Exception {

        Tenant activeTenant = saveTenant("active");
        Tenant otherTenant = saveTenant("other");
        PublicEntity activeEntity = saveEntity(
                activeTenant,
                "Prefeitura Ativa",
                "11222333000181"
        );
        saveEntity(otherTenant, "Prefeitura Externa", "11222333000181");
        String accessToken = accessToken(
                activeTenant.getId(),
                Set.of("VIEWER"),
                Set.of("PUBLIC_ENTITY_READ")
        );

        mockMvc.perform(get("/public-entities")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("name", "Prefeitura")
                        .param("cnpj", "11.222.333/0001-81")
                        .param("type", "CITY_HALL")
                        .param("municipality", "Camp")
                        .param("state", "sp")
                        .param("active", "true")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(
                        activeEntity.getId().toString()
                ));
    }


    @Test
    @Transactional
    void shouldHideAnotherTenantEvenFromPlatformAdministrator() throws Exception {

        Tenant activeTenant = saveTenant("platform-active");
        Tenant otherTenant = saveTenant("platform-other");
        PublicEntity otherEntity = saveEntity(otherTenant, "Órgão externo");
        String accessToken = accessToken(
                activeTenant.getId(),
                Set.of("PLATFORM_ADMIN"),
                Set.of("PUBLIC_ENTITY_READ")
        );

        mockMvc.perform(get("/public-entities/{id}", otherEntity.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }


    @Test
    @Transactional
    void shouldDenyUpdateWithoutPermission() throws Exception {

        Tenant tenant = saveTenant("forbidden");
        PublicEntity entity = saveEntity(tenant, "Prefeitura Protegida");
        String accessToken = accessToken(
                tenant.getId(),
                Set.of("VIEWER"),
                Set.of("PUBLIC_ENTITY_READ")
        );

        mockMvc.perform(put("/public-entities/{id}", entity.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }


    @Test
    @Transactional
    void shouldUpdateAndDeactivateWithPermission() throws Exception {

        Tenant tenant = saveTenant("update");
        PublicEntity entity = saveEntity(tenant, "Prefeitura Original");
        String accessToken = accessToken(
                tenant.getId(),
                Set.of("CONSULTANT"),
                Set.of("PUBLIC_ENTITY_UPDATE")
        );

        mockMvc.perform(put("/public-entities/{id}", entity.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Prefeitura Atualizada"))
                .andExpect(jsonPath("$.active").value(false));
    }


    @Test
    @Transactional
    void shouldReturnBadRequestForInvalidTypeFilter() throws Exception {

        Tenant tenant = saveTenant("invalid-filter");
        String accessToken = accessToken(
                tenant.getId(),
                Set.of("VIEWER"),
                Set.of("PUBLIC_ENTITY_READ")
        );

        mockMvc.perform(get("/public-entities")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("type", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parâmetro inválido: type"));
    }


    @Test
    @Transactional
    void shouldTreatLikeWildcardsAsLiteralCharacters() throws Exception {

        Tenant tenant = saveTenant("wildcard");
        PublicEntity expected = saveEntity(tenant, "Órgão 100% Legal");
        saveEntity(tenant, "Órgão 1000 Legal");
        String accessToken = accessToken(
                tenant.getId(),
                Set.of("VIEWER"),
                Set.of("PUBLIC_ENTITY_READ")
        );

        mockMvc.perform(get("/public-entities")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("name", "Órgão 100%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(
                        expected.getId().toString()
                ));
    }


    @Test
    @Transactional
    void shouldEnforceCnpjUniquenessOnlyWithinTenant() throws Exception {

        Tenant firstTenant = saveTenant("cnpj-first");
        Tenant secondTenant = saveTenant("cnpj-second");
        String firstToken = accessToken(
                firstTenant.getId(),
                Set.of("CONSULTANT"),
                Set.of("PUBLIC_ENTITY_CREATE")
        );
        String secondToken = accessToken(
                secondTenant.getId(),
                Set.of("CONSULTANT"),
                Set.of("PUBLIC_ENTITY_CREATE")
        );

        createEntity(firstToken, createRequest(null), 201);
        createEntity(firstToken, createRequest(null), 201);
        createEntity(firstToken, createRequest("11.222.333/0001-81"), 201);
        createEntity(firstToken, createRequest("11.222.333/0001-81"), 400);
        createEntity(secondToken, createRequest("11.222.333/0001-81"), 201);
    }


    @Test
    @Transactional
    void shouldValidateRequiredFieldsAfterTrimming() throws Exception {

        Tenant tenant = saveTenant("trim-validation");
        String accessToken = accessToken(
                tenant.getId(),
                Set.of("CONSULTANT"),
                Set.of("PUBLIC_ENTITY_CREATE")
        );
        String request = createRequest(null)
                .replace("Prefeitura Municipal", " a ");

        mockMvc.perform(post("/public-entities")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("name")
                ));
    }


    @Test
    void shouldAllowOnlyOneConcurrentCreationWithSameCnpj() throws Exception {

        Tenant tenant = saveTenant("concurrent-cnpj");
        String accessToken = accessToken(
                tenant.getId(),
                Set.of("CONSULTANT"),
                Set.of("PUBLIC_ENTITY_CREATE")
        );
        String request = createRequest("11.222.333/0001-81");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Integer> first = executor.submit(() -> concurrentCreate(
                    accessToken,
                    request,
                    ready,
                    start
            ));
            Future<Integer> second = executor.submit(() -> concurrentCreate(
                    accessToken,
                    request,
                    ready,
                    start
            ));

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            List<Integer> statuses = new ArrayList<>(List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            ));
            Collections.sort(statuses);

            assertEquals(List.of(201, 400), statuses);
        } finally {
            executor.shutdownNow();
        }
    }


    private Tenant saveTenant(String suffix) {

        return tenantRepository.saveAndFlush(Tenant.builder()
                .name("Tenant " + suffix)
                .slug("tenant-" + suffix + "-" + UUID.randomUUID())
                .build());
    }


    private PublicEntity saveEntity(Tenant tenant, String name) {

        return saveEntity(tenant, name, null);
    }


    private PublicEntity saveEntity(
            Tenant tenant,
            String name,
            String cnpj
    ) {

        return publicEntityRepository.saveAndFlush(PublicEntity.builder()
                .tenant(tenant)
                .name(name)
                .cnpj(cnpj)
                .type(PublicEntityType.CITY_HALL)
                .municipality("Campinas")
                .state("SP")
                .active(true)
                .build());
    }


    private String accessToken(
            UUID tenantId,
            Set<String> roles,
            Set<String> permissions
    ) {

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@nomion.com")
                .build();
        TenantMembership membership = TenantMembership.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tenant(Tenant.builder().id(tenantId).build())
                .build();

        return jwtService.generateAccessToken(
                user,
                membership,
                new AuthorizationContext(roles, permissions)
        );
    }


    private String createRequest(String cnpj) {

        String cnpjValue = cnpj == null ? "null" : "\"" + cnpj + "\"";

        return """
                {
                  "name": "Prefeitura Municipal",
                  "cnpj": %s,
                  "type": "CITY_HALL",
                  "municipality": "Campinas",
                  "state": "sp",
                  "email": "CONTATO@example.com",
                  "phone": "(19) 99999-9999"
                }
                """.formatted(cnpjValue);
    }


    private void createEntity(
            String accessToken,
            String request,
            int expectedStatus
    ) throws Exception {

        mockMvc.perform(post("/public-entities")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().is(expectedStatus));
    }


    private int concurrentCreate(
            String accessToken,
            String request,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {

        ready.countDown();
        start.await(5, TimeUnit.SECONDS);

        return mockMvc.perform(post("/public-entities")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andReturn()
                .getResponse()
                .getStatus();
    }


    private String updateRequest() {

        return """
                {
                  "name": "Prefeitura Atualizada",
                  "cnpj": null,
                  "type": "CITY_HALL",
                  "municipality": "Campinas",
                  "state": "SP",
                  "email": null,
                  "phone": null,
                  "active": false
                }
                """;
    }
}
