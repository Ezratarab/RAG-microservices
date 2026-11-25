package com.example.common.security;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The security setup every servlet service shares: stateless JWT resource server,
 * health endpoints open for container health checks, everything else authenticated
 * unless the service opens a path explicitly.
 */
public final class ResourceServerDefaults {

    private ResourceServerDefaults() {
    }

    public static SecurityFilterChain build(
            HttpSecurity http,
            KeycloakJwtConverter converter,
            Customizer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry> extraRules
    ) throws Exception {
        http
                // Stateless bearer-token API: no cookies, so CSRF protection isn't needed.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health/**").permitAll();
                    extraRules.customize(auth);
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));
        return http.build();
    }
}
