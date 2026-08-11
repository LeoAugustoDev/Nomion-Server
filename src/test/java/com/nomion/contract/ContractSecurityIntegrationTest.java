package com.nomion.contract;

import com.nomion.contract.domain.Contract;
import com.nomion.contract.domain.ContractStatus;
import com.nomion.contract.repository.ContractRepository;
import com.nomion.publicentity.domain.PublicEntity;
import com.nomion.publicentity.domain.PublicEntityType;
import com.nomion.publicentity.repository.PublicEntityRepository;
import com.nomion.security.AuthorizationContext;
import com.nomion.security.JwtService;
import com.nomion.tenant.domain.Tenant;
import com.nomion.tenant.domain.TenantMembership;
import com.nomion.tenant.domain.TenantMembershipStatus;
import com.nomion.tenant.repository.TenantMembershipRepository;
import com.nomion.tenant.repository.TenantRepository;
import com.nomion.user.domain.User;
import com.nomion.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ContractSecurityIntegrationTest {


    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantMembershipRepository membershipRepository;

    @Autowired
    private PublicEntityRepository publicEntityRepository;

    @Autowired
    private ContractRepository contractRepository;


    @Test
    @Transactional
    void shouldCreateActiveContractAndNormalizeNumber() throws Exception {

        Fixture fixture = createFixture("create");
        String accessToken = accessToken(
                fixture,
                Set.of("CONSULTANT"),
                Set.of("CONTRACT_CREATE")
        );

        mockMvc.perform(post("/contracts")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contractRequest(
                                fixture,
                                " ct-001/2026 ",
                                ContractStatus.ACTIVE,
                                fixture.membership().getId()
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value("CT-001/2026"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.publicEntity.id").value(
                        fixture.publicEntity().getId().toString()
                ))
                .andExpect(jsonPath("$.manager.membershipId").value(
                        fixture.membership().getId().toString()
                ))
                .andExpect(jsonPath("$.amount").value(100000.00))
                .andExpect(jsonPath("$.createdByUserId").value(
                        fixture.user().getId().toString()
                ))
                .andExpect(jsonPath("$.updatedByUserId").value(
                        fixture.user().getId().toString()
                ));
    }


    @Test
    @Transactional
    void shouldListOnlyActiveTenantWithAllFilters() throws Exception {

        Fixture activeFixture = createFixture("list-active");
        Fixture otherFixture = createFixture("list-other");
        Contract activeContract = saveContract(activeFixture, "CT-100/2026");
        saveContract(otherFixture, "CT-100/2026");
        String accessToken = accessToken(
                activeFixture,
                Set.of("VIEWER"),
                Set.of("CONTRACT_READ")
        );

        mockMvc.perform(get("/contracts")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("number", "CT-1")
                        .param(
                                "publicEntityId",
                                activeFixture.publicEntity().getId().toString()
                        )
                        .param(
                                "managerMembershipId",
                                activeFixture.membership().getId().toString()
                        )
                        .param("status", "ACTIVE")
                        .param("startDateFrom", "2026-01-01")
                        .param("endDateTo", "2026-12-31")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(
                        activeContract.getId().toString()
                ));

        mockMvc.perform(get("/contracts")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }


    @Test
    @Transactional
    void shouldHideAnotherTenantEvenFromPlatformAdministrator() throws Exception {

        Fixture activeFixture = createFixture("platform-active");
        Fixture otherFixture = createFixture("platform-other");
        Contract otherContract = saveContract(otherFixture, "CT-200/2026");
        String accessToken = accessToken(
                activeFixture,
                Set.of("PLATFORM_ADMIN"),
                Set.of("CONTRACT_READ")
        );

        mockMvc.perform(get("/contracts/{id}", otherContract.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }


    @Test
    @Transactional
    void shouldEnforceViewerReadOnlyAccess() throws Exception {

        Fixture fixture = createFixture("viewer");
        Contract contract = saveContract(fixture, "CT-300/2026");
        String accessToken = accessToken(
                fixture,
                Set.of("VIEWER"),
                Set.of("CONTRACT_READ")
        );

        mockMvc.perform(get("/contracts/{id}", contract.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/contracts")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contractRequest(
                                fixture,
                                "CT-301/2026",
                                ContractStatus.DRAFT,
                                null
                        )))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/contracts/{id}", contract.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contractRequest(
                                fixture,
                                contract.getNumber(),
                                ContractStatus.SUSPENDED,
                                fixture.membership().getId()
                        )))
                .andExpect(status().isForbidden());
    }


    @Test
    @Transactional
    void shouldValidateStatusLifecycleAndTermination() throws Exception {

        Fixture fixture = createFixture("lifecycle");
        Contract contract = saveContract(
                fixture,
                "CT-400/2026",
                ContractStatus.DRAFT,
                null
        );
        String accessToken = accessToken(
                fixture,
                Set.of("CONSULTANT"),
                Set.of("CONTRACT_UPDATE")
        );

        updateContract(
                accessToken,
                contract,
                contractRequest(
                        fixture,
                        contract.getNumber(),
                        ContractStatus.ACTIVE,
                        null
                ),
                400
        );
        updateContract(
                accessToken,
                contract,
                contractRequest(
                        fixture,
                        contract.getNumber(),
                        ContractStatus.ACTIVE,
                        fixture.membership().getId()
                ),
                200
        );
        updateContract(
                accessToken,
                contract,
                contractRequest(
                        fixture,
                        contract.getNumber(),
                        ContractStatus.TERMINATED,
                        fixture.membership().getId()
                ),
                200
        );
        updateContract(
                accessToken,
                contract,
                contractRequest(
                        fixture,
                        contract.getNumber(),
                        ContractStatus.TERMINATED,
                        fixture.membership().getId()
                ),
                400
        );
    }


    @Test
    @Transactional
    void shouldAllowSameNumberInDifferentTenants() throws Exception {

        Fixture firstFixture = createFixture("number-first");
        Fixture secondFixture = createFixture("number-second");

        createContract(
                accessToken(
                        firstFixture,
                        Set.of("CONSULTANT"),
                        Set.of("CONTRACT_CREATE")
                ),
                contractRequest(
                        firstFixture,
                        "CT-SHARED/2026",
                        ContractStatus.DRAFT,
                        null
                ),
                201
        );
        createContract(
                accessToken(
                        secondFixture,
                        Set.of("CONSULTANT"),
                        Set.of("CONTRACT_CREATE")
                ),
                contractRequest(
                        secondFixture,
                        "CT-SHARED/2026",
                        ContractStatus.DRAFT,
                        null
                ),
                201
        );
    }


    @Test
    @Transactional
    void shouldRejectInvalidDates() throws Exception {

        Fixture fixture = createFixture("invalid-date");
        String accessToken = accessToken(
                fixture,
                Set.of("CONSULTANT"),
                Set.of("CONTRACT_CREATE")
        );
        String request = contractRequest(
                fixture,
                "CT-500/2026",
                ContractStatus.DRAFT,
                null
        ).replace("2026-01-01", "2027-01-01");

        createContract(accessToken, request, 400);
    }


    @Test
    void shouldAllowOnlyOneConcurrentCreationWithSameNumber() throws Exception {

        Fixture fixture = createFixture("concurrent");
        String accessToken = accessToken(
                fixture,
                Set.of("CONSULTANT"),
                Set.of("CONTRACT_CREATE")
        );
        String request = contractRequest(
                fixture,
                "CT-CONCURRENT/2026",
                ContractStatus.DRAFT,
                null
        );
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


    @Test
    @Transactional
    void shouldEnforcePublicEntityTenantIntegrityInDatabase() {

        Fixture firstFixture = createFixture("integrity-first");
        Fixture secondFixture = createFixture("integrity-second");
        Contract invalidContract = Contract.builder()
                .tenant(firstFixture.tenant())
                .publicEntity(secondFixture.publicEntity())
                .number("CT-INVALID/2026")
                .object("Contrato inválido entre tenants")
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .status(ContractStatus.DRAFT)
                .createdByUserId(firstFixture.user().getId())
                .updatedByUserId(firstFixture.user().getId())
                .build();

        assertThrows(
                DataIntegrityViolationException.class,
                () -> contractRepository.saveAndFlush(invalidContract)
        );
    }


    @Test
    @Transactional
    void shouldEnforceManagerTenantIntegrityInDatabase() {

        Fixture firstFixture = createFixture("manager-integrity-first");
        Fixture secondFixture = createFixture("manager-integrity-second");
        Contract invalidContract = Contract.builder()
                .tenant(firstFixture.tenant())
                .publicEntity(firstFixture.publicEntity())
                .managerMembership(secondFixture.membership())
                .number("CT-INVALID-MANAGER/2026")
                .object("Contrato com responsável de outro tenant")
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .status(ContractStatus.ACTIVE)
                .createdByUserId(firstFixture.user().getId())
                .updatedByUserId(firstFixture.user().getId())
                .build();

        assertThrows(
                DataIntegrityViolationException.class,
                () -> contractRepository.saveAndFlush(invalidContract)
        );
    }


    @Test
    @Transactional
    void shouldEnforceAuditActorTenantIntegrityInDatabase() {

        Fixture firstFixture = createFixture("audit-integrity-first");
        Fixture secondFixture = createFixture("audit-integrity-second");
        Contract invalidContract = Contract.builder()
                .tenant(firstFixture.tenant())
                .publicEntity(firstFixture.publicEntity())
                .number("CT-INVALID-ACTOR/2026")
                .object("Contrato com auditor de outro tenant")
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .status(ContractStatus.DRAFT)
                .createdByUserId(secondFixture.user().getId())
                .updatedByUserId(secondFixture.user().getId())
                .build();

        assertThrows(
                DataIntegrityViolationException.class,
                () -> contractRepository.saveAndFlush(invalidContract)
        );
    }


    @Test
    @Transactional
    void shouldEnforceManagerForActiveContractInDatabase() {

        Fixture fixture = createFixture("active-manager-integrity");
        Contract invalidContract = Contract.builder()
                .tenant(fixture.tenant())
                .publicEntity(fixture.publicEntity())
                .number("CT-ACTIVE-WITHOUT-MANAGER/2026")
                .object("Contrato ativo sem responsável")
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .status(ContractStatus.ACTIVE)
                .createdByUserId(fixture.user().getId())
                .updatedByUserId(fixture.user().getId())
                .build();

        assertThrows(
                DataIntegrityViolationException.class,
                () -> contractRepository.saveAndFlush(invalidContract)
        );
    }


    @Test
    @Transactional
    void shouldPreventDeactivationOfPublicEntityWithActiveContract() throws Exception {

        Fixture fixture = createFixture("deactivation-guard");
        saveContract(fixture, "CT-ACTIVE-GUARD/2026");
        String accessToken = accessToken(
                fixture,
                Set.of("CONSULTANT"),
                Set.of("PUBLIC_ENTITY_UPDATE")
        );
        String request = """
                {
                  "name": "Prefeitura atualizada",
                  "cnpj": null,
                  "type": "CITY_HALL",
                  "municipality": "Campinas",
                  "state": "SP",
                  "email": null,
                  "phone": null,
                  "active": false
                }
                """;

        mockMvc.perform(put(
                        "/public-entities/{id}",
                        fixture.publicEntity().getId()
                )
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Órgão público possui contrato ativo"
                ));
    }


    private Fixture createFixture(String suffix) {

        Tenant tenant = tenantRepository.saveAndFlush(Tenant.builder()
                .name("Tenant " + suffix)
                .slug("contract-" + suffix + "-" + UUID.randomUUID())
                .build());
        User user = userRepository.saveAndFlush(User.builder()
                .name("User " + suffix)
                .email("contract-" + suffix + "-" + UUID.randomUUID()
                        + "@nomion.com")
                .password("encoded-password")
                .build());
        TenantMembership membership = membershipRepository.saveAndFlush(
                TenantMembership.builder()
                        .tenant(tenant)
                        .user(user)
                        .status(TenantMembershipStatus.ACTIVE)
                        .build()
        );
        PublicEntity publicEntity = publicEntityRepository.saveAndFlush(
                PublicEntity.builder()
                        .tenant(tenant)
                        .name("Prefeitura " + suffix)
                        .type(PublicEntityType.CITY_HALL)
                        .municipality("Campinas")
                        .state("SP")
                        .active(true)
                        .build()
        );

        return new Fixture(tenant, user, membership, publicEntity);
    }


    private Contract saveContract(Fixture fixture, String number) {

        return saveContract(
                fixture,
                number,
                ContractStatus.ACTIVE,
                fixture.membership()
        );
    }


    private Contract saveContract(
            Fixture fixture,
            String number,
            ContractStatus status,
            TenantMembership manager
    ) {

        return contractRepository.saveAndFlush(Contract.builder()
                .tenant(fixture.tenant())
                .publicEntity(fixture.publicEntity())
                .managerMembership(manager)
                .number(number)
                .object("Consultoria técnica especializada")
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .amount(new BigDecimal("100000.00"))
                .status(status)
                .createdByUserId(fixture.user().getId())
                .updatedByUserId(fixture.user().getId())
                .build());
    }


    private String accessToken(
            Fixture fixture,
            Set<String> roles,
            Set<String> permissions
    ) {

        return jwtService.generateAccessToken(
                fixture.user(),
                fixture.membership(),
                new AuthorizationContext(roles, permissions)
        );
    }


    private String contractRequest(
            Fixture fixture,
            String number,
            ContractStatus status,
            UUID managerMembershipId
    ) {

        String manager = managerMembershipId == null
                ? "null"
                : "\"" + managerMembershipId + "\"";

        return """
                {
                  "publicEntityId": "%s",
                  "managerMembershipId": %s,
                  "number": "%s",
                  "object": "Consultoria técnica especializada",
                  "startDate": "2026-01-01",
                  "endDate": "2026-12-31",
                  "amount": 100000.00,
                  "status": "%s"
                }
                """.formatted(
                fixture.publicEntity().getId(),
                manager,
                number,
                status
        );
    }


    private void createContract(
            String accessToken,
            String request,
            int expectedStatus
    ) throws Exception {

        mockMvc.perform(post("/contracts")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().is(expectedStatus));
    }


    private void updateContract(
            String accessToken,
            Contract contract,
            String request,
            int expectedStatus
    ) throws Exception {

        mockMvc.perform(put("/contracts/{id}", contract.getId())
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

        return mockMvc.perform(post("/contracts")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andReturn()
                .getResponse()
                .getStatus();
    }


    private record Fixture(
            Tenant tenant,
            User user,
            TenantMembership membership,
            PublicEntity publicEntity
    ) {
    }
}
