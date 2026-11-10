package com.nomion.security;

import com.nomion.tenant.domain.TenantMembership;
import com.nomion.tenant.service.TenantMembershipService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public class KeycloakTenantContextFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant-Id";

    private final TenantMembershipService membershipService;
    private final AuthorizationService authorizationService;

    public KeycloakTenantContextFilter(
            TenantMembershipService membershipService,
            AuthorizationService authorizationService
    ) {
        this.membershipService = membershipService;
        this.authorizationService = authorizationService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        SecurityContext context = SecurityContextHolder.getContext();
        Enumeration<String> tenantHeaders = request.getHeaders(TENANT_HEADER);

        if (context.getAuthentication() != null
                && context.getAuthentication().getPrincipal() instanceof AuthenticatedUser user
                && tenantHeaders.hasMoreElements()) {
            String tenantHeader = tenantHeaders.nextElement();
            if (tenantHeaders.hasMoreElements()) {
                throw new AccessDeniedException("Seleção de escritório inválida");
            }
            context.setAuthentication(withTenant(user, parseTenantId(tenantHeader)));
        }

        chain.doFilter(request, response);
    }

    private UsernamePasswordAuthenticationToken withTenant(AuthenticatedUser user, UUID tenantId) {
        TenantMembership membership = membershipService.findActive(user.id(), tenantId)
                .orElseThrow(() -> new AccessDeniedException("Acesso ao escritório negado"));
        AuthorizationContext authorization = authorizationService.load(user.id(), membership.getId());
        AuthenticatedUser principal = new AuthenticatedUser(
                user.id(), membership.getId(), tenantId, user.email(),
                authorization.roles(), authorization.permissions()
        );
        List<GrantedAuthority> authorities = Stream.concat(
                        authorization.roles().stream().map(role -> "ROLE_" + role),
                        authorization.permissions().stream()
                )
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();

        return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
    }

    private UUID parseTenantId(String header) {
        try {
            UUID tenantId = UUID.fromString(header);
            if (tenantId.toString().equalsIgnoreCase(header)) {
                return tenantId;
            }
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Seleção de escritório inválida", exception);
        }
        throw new AccessDeniedException("Seleção de escritório inválida");
    }
}
