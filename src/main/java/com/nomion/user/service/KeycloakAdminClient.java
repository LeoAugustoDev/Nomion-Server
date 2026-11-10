package com.nomion.user.service;

import com.nomion.handler.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Profile("keycloak")
public class KeycloakAdminClient {

    private final RestClient client;
    private final String issuer;
    private final String usersEndpoint;
    private final String clientId;
    private final String clientSecret;

    public KeycloakAdminClient(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${KEYCLOAK_PROVISIONING_CLIENT_ID:nomion-provisioning}") String clientId,
            @Value("${KEYCLOAK_PROVISIONING_CLIENT_SECRET:}") String clientSecret
    ) {
        int realmStart = issuer.lastIndexOf("/realms/");
        if (realmStart < 0 || issuer.substring(realmStart + 8).isBlank()) {
            throw new IllegalArgumentException("Emissor do Keycloak deve identificar um realm");
        }
        this.issuer = issuer;
        this.usersEndpoint = issuer.substring(0, realmStart) + "/admin" + issuer.substring(realmStart) + "/users";
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(8));
        this.client = RestClient.builder().requestFactory(requestFactory).build();
    }

    public String issuer() {
        return issuer;
    }

    public String ensureUser(UUID invitationId, String name, String email) {
        String username = "nomion-" + invitationId;
        try {
            String token = accessToken();
            RemoteUser existing = findUser(username, token);
            if (existing != null) {
                if (!email.equalsIgnoreCase(existing.email()) || !StringUtils.hasText(existing.id())) {
                    throw new BusinessException("Identidade remota não corresponde ao convite");
                }
                return existing.id();
            }

            String[] names = name.split("\\s+", 2);
            client.post().uri(usersEndpoint)
                    .headers(headers -> headers.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "username", username, "email", email,
                            "firstName", names[0], "lastName", names.length == 2 ? names[1] : "",
                            "enabled", true, "requiredActions", List.of("VERIFY_EMAIL", "UPDATE_PASSWORD")
                    ))
                    .retrieve().toBodilessEntity();

            RemoteUser created = findUser(username, token);
            if (created == null || !email.equalsIgnoreCase(created.email()) || !StringUtils.hasText(created.id())) {
                throw unavailable();
            }
            return created.id();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.CONFLICT) {
                throw new BusinessException("Já existe uma identidade conflitante no Keycloak");
            }
            throw unavailable();
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    public void sendInvitation(String subject) {
        try {
            client.put().uri(usersEndpoint + "/{subject}/execute-actions-email?lifespan=86400", subject)
                    .headers(headers -> headers.setBearerAuth(accessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of("VERIFY_EMAIL", "UPDATE_PASSWORD"))
                    .retrieve().toBodilessEntity();
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    private RemoteUser findUser(String username, String token) {
        RemoteUser[] matches = client.get().uri(usersEndpoint + "?username={username}&exact=true", username)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve().body(RemoteUser[].class);
        return matches == null ? null : Arrays.stream(matches)
                .filter(user -> username.equals(user.username()))
                .findFirst().orElse(null);
    }

    private String accessToken() {
        if (!StringUtils.hasText(clientSecret)) {
            throw unavailable();
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        AdminToken token = client.post().uri(issuer + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve().body(AdminToken.class);
        if (token == null || !StringUtils.hasText(token.access_token())) {
            throw unavailable();
        }
        return token.access_token();
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Provisionamento indisponível no Keycloak");
    }

    private record AdminToken(String access_token) {}

    private record RemoteUser(String id, String username, String email) {}
}
