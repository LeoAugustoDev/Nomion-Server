package com.nomion.user.service;

import com.nomion.handler.BusinessException;
import com.nomion.security.AuthenticatedUser;
import com.nomion.user.domain.RoleCode;
import com.nomion.user.domain.User;
import com.nomion.user.dto.InviteUserRequest;
import com.nomion.user.dto.UserResponse;
import com.nomion.user.repository.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Profile("keycloak")
public class UserInvitationService {

    private final JdbcClient database;
    private final TransactionTemplate transactions;
    private final KeycloakAdminClient keycloak;
    private final UserService userService;
    private final UserRepository users;

    public UserInvitationService(
            JdbcClient database, PlatformTransactionManager transactionManager,
            KeycloakAdminClient keycloak, UserService userService, UserRepository users
    ) {
        this.database = database;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.keycloak = keycloak;
        this.userService = userService;
        this.users = users;
    }

    public void invite(UUID requestId, InviteUserRequest request, AuthenticatedUser administrator) {
        UUID tenantId = administrator.tenantId();
        if (tenantId == null) {
            throw new AccessDeniedException("Selecione um escritório");
        }
        if (request.roles().contains(RoleCode.PLATFORM_ADMIN)) {
            throw new BusinessException("Perfil de plataforma inválido para o tenant");
        }

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String name = request.name().trim();
        String roles = request.roles().stream().map(RoleCode::name).sorted().collect(Collectors.joining(","));

        // Reserve the remote username before calling Keycloak.
        transactions.executeWithoutResult(status -> database.sql("""
                INSERT INTO user_invitations (id, tenant_id, request_id, email, name, roles)
                SELECT ?, ?, ?, ?, ?, ?
                WHERE NOT EXISTS (SELECT 1 FROM users WHERE LOWER(email) = ?)
                ON CONFLICT DO NOTHING
                """).params(UUID.randomUUID(), tenantId, requestId, email, name, roles, email).update());

        transactions.executeWithoutResult(status -> {
            Invitation invitation = lockInvitation(tenantId, requestId);
            if (!email.equals(invitation.email()) || !name.equals(invitation.name())
                    || !roles.equals(invitation.roles())) {
                throw new BusinessException("Chave de idempotência já utilizada com outros dados");
            }
            if (invitation.userId() == null) {
                if (users.existsByEmailIgnoreCase(email)) {
                    throw new BusinessException("E-mail já utilizado");
                }
                String subject = keycloak.ensureUser(invitation.id(), name, email);
                UserResponse user = userService.createInvitedUser(User.builder()
                        .name(name).email(email)
                        .identityIssuer(keycloak.issuer()).identitySubject(subject)
                        .build(), request.roles(), administrator);
                database.sql("UPDATE user_invitations SET user_id = ? WHERE id = ?")
                        .params(user.id(), invitation.id()).update();
            }
        });

        // Send only after the user and membership have committed.
        transactions.executeWithoutResult(status -> {
            Invitation invitation = lockInvitation(tenantId, requestId);
            if (!invitation.sent()) {
                User user = users.findById(invitation.userId())
                        .orElseThrow(() -> new IllegalStateException("Usuário do convite não encontrado"));
                if (!keycloak.issuer().equals(user.getIdentityIssuer())
                        || !email.equals(user.getEmail())) {
                    throw new BusinessException("Identidade atual não corresponde ao convite");
                }
                keycloak.sendInvitation(user.getIdentitySubject());
                database.sql("UPDATE user_invitations SET sent_at = CURRENT_TIMESTAMP WHERE id = ?")
                        .param(invitation.id()).update();
            }
        });
    }

    private Invitation lockInvitation(UUID tenantId, UUID requestId) {
        return database.sql("""
                SELECT id, email, name, roles, user_id, sent_at IS NOT NULL AS sent
                FROM user_invitations WHERE tenant_id = ? AND request_id = ? FOR UPDATE
                """).params(tenantId, requestId)
                .query((row, index) -> new Invitation(
                        row.getObject("id", UUID.class), row.getString("email"), row.getString("name"),
                        row.getString("roles"), row.getObject("user_id", UUID.class), row.getBoolean("sent")
                )).optional()
                .orElseThrow(() -> new BusinessException("E-mail já utilizado ou com convite pendente"));
    }

    private record Invitation(UUID id, String email, String name, String roles, UUID userId, boolean sent) {}
}
