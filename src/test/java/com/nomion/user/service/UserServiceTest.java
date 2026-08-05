package com.nomion.user.service;

import com.nomion.handler.BusinessException;
import com.nomion.handler.ResourceNotFoundException;
import com.nomion.security.AuthenticatedUser;
import com.nomion.tenant.domain.Tenant;
import com.nomion.tenant.domain.TenantMembership;
import com.nomion.tenant.domain.TenantMembershipStatus;
import com.nomion.tenant.dto.TenantMembershipResponse;
import com.nomion.tenant.repository.TenantRepository;
import com.nomion.tenant.service.TenantMembershipService;
import com.nomion.user.domain.User;
import com.nomion.user.dto.CreateUserRequest;
import com.nomion.user.dto.UserResponse;
import com.nomion.user.mapper.UserMapper;
import com.nomion.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {


    @Mock
    private UserRepository repository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantMembershipService membershipService;

    @Mock
    private UserMapper mapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService service;


    @Test
    void shouldCreateGlobalUserWithInitialMembership() {

        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();

        CreateUserRequest request = new CreateUserRequest(
                "Leonardo Alves",
                "LEONARDO@NOMION.COM",
                "senha-segura",
                tenantId
        );

        Tenant tenant = Tenant.builder()
                .id(tenantId)
                .name("Escritório Nomion")
                .slug("nomion")
                .build();

        User user = User.builder()
                .id(userId)
                .name("Leonardo Alves")
                .email("leonardo@nomion.com")
                .password("encoded-password")
                .build();

        TenantMembership membership = TenantMembership.builder()
                .id(membershipId)
                .user(user)
                .tenant(tenant)
                .status(TenantMembershipStatus.ACTIVE)
                .build();

        UserResponse response = new UserResponse(
                userId,
                "Leonardo Alves",
                "leonardo@nomion.com",
                List.of(new TenantMembershipResponse(
                        membershipId,
                        tenantId,
                        "Escritório Nomion",
                        "nomion",
                        TenantMembershipStatus.ACTIVE
                ))
        );

        when(repository.existsByEmailIgnoreCase("leonardo@nomion.com")).thenReturn(false);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(passwordEncoder.encode("senha-segura")).thenReturn("encoded-password");
        when(mapper.toEntity(
                "Leonardo Alves",
                "leonardo@nomion.com",
                "encoded-password"
        )).thenReturn(user);
        when(repository.saveAndFlush(user)).thenReturn(user);
        when(membershipService.create(user, tenant)).thenReturn(membership);
        when(mapper.toResponse(user, List.of(membership))).thenReturn(response);

        UserResponse result = service.create(request);

        assertEquals(response, result);

        verify(passwordEncoder).encode("senha-segura");
        verify(repository).saveAndFlush(user);
        verify(membershipService).create(user, tenant);
    }


    @Test
    void shouldReturnUserWithAllActiveMemberships() {

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        TenantMembership membership = TenantMembership.builder()
                .id(membershipId)
                .tenant(Tenant.builder().id(tenantId).build())
                .build();
        UserResponse response = new UserResponse(userId, "Leonardo", "email@nomion.com", List.of());
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                UUID.randomUUID(),
                UUID.randomUUID(),
                tenantId,
                "admin@nomion.com"
        );

        when(repository.findById(userId)).thenReturn(Optional.of(user));
        when(membershipService.findActive(userId, tenantId)).thenReturn(Optional.of(membership));
        when(mapper.toResponse(user, List.of(membership))).thenReturn(response);

        UserResponse result = service.findById(userId, authenticatedUser);

        assertEquals(response, result);
    }


    @Test
    void shouldHideUserFromAnotherTenant() {

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                UUID.randomUUID(),
                UUID.randomUUID(),
                tenantId,
                "admin@nomion.com"
        );

        when(repository.findById(userId)).thenReturn(Optional.of(user));
        when(membershipService.findActive(userId, tenantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.findById(userId, authenticatedUser));

        verify(mapper, never()).toResponse(any(), any());
    }


    @Test
    void shouldRejectExistingEmail() {

        CreateUserRequest request = new CreateUserRequest(
                "Leonardo Alves",
                "leonardo@nomion.com",
                "senha-segura",
                UUID.randomUUID()
        );

        when(repository.existsByEmailIgnoreCase("leonardo@nomion.com")).thenReturn(true);

        assertThrows(BusinessException.class, () -> service.create(request));

        verify(tenantRepository, never()).findById(any());
        verify(repository, never()).saveAndFlush(any());
    }


    @Test
    void shouldRejectUserCreationWhenTenantDoesNotExist() {

        UUID tenantId = UUID.randomUUID();

        CreateUserRequest request = new CreateUserRequest(
                "Leonardo Alves",
                "leonardo@nomion.com",
                "senha-segura",
                tenantId
        );

        when(repository.existsByEmailIgnoreCase("leonardo@nomion.com")).thenReturn(false);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.create(request));

        verify(repository, never()).saveAndFlush(any());
        verify(membershipService, never()).create(any(), any());
    }
}
