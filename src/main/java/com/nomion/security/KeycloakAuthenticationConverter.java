package com.nomion.security;

import com.nomion.user.domain.User;
import com.nomion.user.repository.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

@Component
@Profile("keycloak")
public class KeycloakAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;

    public KeycloakAuthenticationConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String issuer = jwt.getClaimAsString("iss");
        String subject = jwt.getSubject();

        if (!StringUtils.hasText(subject) || jwt.getExpiresAt() == null
                || !"Bearer".equals(jwt.getClaims().get("typ"))) {
            throw new InvalidBearerTokenException("Token de acesso inválido");
        }

        User user = userRepository.findByIdentityIssuerAndIdentitySubject(issuer, subject)
                .orElseThrow(() -> new InvalidBearerTokenException("Identidade sem vínculo no Nomion"));

        AuthenticatedUser principal = new AuthenticatedUser(
                user.getId(), null, null, user.getEmail(), Set.of(), Set.of()
        );

        return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
    }
}
