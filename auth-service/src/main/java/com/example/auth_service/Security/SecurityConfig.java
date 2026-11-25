package com.example.auth_service.Security;

import com.example.common.security.KeycloakJwtConverter;
import com.example.common.security.ResourceServerDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Profile(Constants.PROFILE_PRODUCTION)
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public KeycloakJwtConverter jwtConverter(
            @Value("${jwt.auth.converter.principle-attribute}") String principalAttribute,
            @Value("${jwt.auth.converter.resource-id}") String resourceId) {
        return new KeycloakJwtConverter(principalAttribute, resourceId);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, KeycloakJwtConverter converter) throws Exception {
        // Only sign-up is public; every other /auth endpoint needs a token (and its @PreAuthorize role).
        return ResourceServerDefaults.build(http, converter,
                auth -> auth.requestMatchers(HttpMethod.POST, "/auth").permitAll());
    }
}
