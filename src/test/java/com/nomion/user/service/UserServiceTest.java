package com.nomion.user.service;

import com.nomion.handler.BusinessException;
import com.nomion.handler.ResourceNotFoundException;
import com.nomion.tenant.domain.Tenant;
import com.nomion.tenant.repository.TenantRepository;
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
    private UserMapper mapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService service;


    @Test
    void shouldCreateUserWithEncodedPassword() {

        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        CreateUserRequest request = new CreateUserRequest(
                "Leonardo Alves",
                "LEONARDO@NOMION.COM",
                "senha-segura",
                tenantId
        );

        Tenant tenant = Tenant.builder()
                .id(tenantId)
                .build();

        User user = User.builder()
                .id(userId)
                .name("Leonardo Alves")
                .email("leonardo@nomion.com")
                .password("encoded-password")
                .tenant(tenant)
                .build();

        UserResponse response = new UserResponse(
                userId,
                "Leonardo Alves",
                "leonardo@nomion.com",
                tenantId
        );

        when(repository.existsByEmailIgnoreCase("leonardo@nomion.com")).thenReturn(false);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(passwordEncoder.encode("senha-segura")).thenReturn("encoded-password");
        when(mapper.toEntity(
                "Leonardo Alves",
                "leonardo@nomion.com",
                "encoded-password",
                tenant
        )).thenReturn(user);
        when(repository.save(user)).thenReturn(user);
        when(mapper.toResponse(user)).thenReturn(response);

        UserResponse result = service.create(request);

        assertEquals(response, result);

        verify(passwordEncoder).encode("senha-segura");
        verify(repository).save(user);
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
        verify(repository, never()).save(any());
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

        verify(repository, never()).save(any());
    }
}
