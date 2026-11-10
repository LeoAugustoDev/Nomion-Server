package com.nomion.config;

import com.nomion.security.JwtAuthenticationFilter;
import com.nomion.security.AuthorizationService;
import com.nomion.security.KeycloakAuthenticationConverter;
import com.nomion.security.KeycloakTenantContextFilter;
import com.nomion.security.RestAuthenticationEntryPoint;
import com.nomion.tenant.service.TenantMembershipService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, InitialSetupProperties.class})
public class SecurityConfig {

    @Bean
    @Profile("!keycloak")
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint
    ) throws Exception {

        configureStatelessSecurity(http, authenticationEntryPoint)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                HttpMethod.POST,
                                "/auth/login",
                                "/auth/select-tenant",
                                "/auth/refresh",
                                "/auth/logout",
                                "/setup/initialize"
                        ).permitAll()
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }


    @Bean
    @Profile("keycloak")
    @Order(1)
    public SecurityFilterChain keycloakSetupSecurityFilterChain(
            HttpSecurity http, RestAuthenticationEntryPoint authenticationEntryPoint
    ) throws Exception {
        return configureStatelessSecurity(http, authenticationEntryPoint)
                .securityMatcher(PathPatternRequestMatcher.withDefaults()
                        .matcher(HttpMethod.POST, "/setup/initialize"))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .jwt(withDefaults()))
                .build();
    }

    @Bean
    @Profile("keycloak")
    public SecurityFilterChain keycloakSecurityFilterChain(
            HttpSecurity http,
            KeycloakAuthenticationConverter authenticationConverter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            TenantMembershipService membershipService,
            AuthorizationService authorizationService
    ) throws Exception {

        configureStatelessSecurity(http, authenticationEntryPoint)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**", "/setup/**").denyAll()
                        .requestMatchers(HttpMethod.POST, "/users").denyAll()
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter))
                )
                .addFilterBefore(
                        new KeycloakTenantContextFilter(membershipService, authorizationService),
                        AuthorizationFilter.class
                );

        return http.build();
    }

    private HttpSecurity configureStatelessSecurity(
            HttpSecurity http,
            RestAuthenticationEntryPoint authenticationEntryPoint
    ) throws Exception {

        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                );
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
