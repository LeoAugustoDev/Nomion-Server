package com.nomion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nomion.initial-setup")
public record InitialSetupProperties(

        boolean enabled,

        String token

) {

    public InitialSetupProperties {

        token = token == null ? "" : token;

        if (enabled && token.length() < 32) {
            throw new IllegalArgumentException(
                    "Token do setup inicial deve possuir ao menos 32 caracteres"
            );
        }
    }
}
