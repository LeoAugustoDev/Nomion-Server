package com.nomion.user.service;

import com.nomion.handler.BusinessException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class KeycloakInvitationResendTest {
    private HttpServer server;
    private KeycloakAdminClient client;
    private boolean verified;
    private boolean password;
    private final AtomicReference<String> sentActions = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String response;
            if (path.endsWith("/token")) response = "{\"access_token\":\"token\"}";
            else if (path.endsWith("/credentials")) response = password ? "[{\"type\":\"password\"}]" : "[]";
            else if (path.endsWith("/execute-actions-email")) {
                sentActions.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            } else response = "{\"id\":\"user\",\"emailVerified\":" + verified + "}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        client = new KeycloakAdminClient("http://127.0.0.1:" + server.getAddress().getPort() + "/realms/nomion", "test", "secret");
    }

    @AfterEach
    void stop() { server.stop(0); }

    @Test
    void requestsOnlyMissingActionsAndDoesNotResetAnExistingPassword() {
        password = true;
        client.resendPendingInvitation("user");
        assertEquals("[\"VERIFY_EMAIL\"]", sentActions.get());
    }

    @Test
    void requestsPasswordWhenEmailIsAlreadyVerified() {
        verified = true;
        client.resendPendingInvitation("user");
        assertEquals("[\"UPDATE_PASSWORD\"]", sentActions.get());
    }

    @Test
    void refusesToResendWhenAccountIsReady() {
        verified = true;
        password = true;
        assertThrows(BusinessException.class, () -> client.resendPendingInvitation("user"));
        assertNull(sentActions.get());
    }
}
