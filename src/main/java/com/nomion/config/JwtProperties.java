package com.nomion.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "nomion.security.jwt")
public record JwtProperties(

        @NotBlank
        String secret,

        @NotNull
        Duration accessTokenExpiration,

        @NotNull
        Duration refreshTokenExpiration

) {
}
