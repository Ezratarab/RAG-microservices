package com.example.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.security.Principal;
import java.util.Optional;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    /**
     * First line of defence. Each service still validates the token itself (defence in depth),
     * so a request that somehow bypasses the gateway is still rejected.
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(auth -> auth
                        .pathMatchers("/actuator/health/**").permitAll()
                        .pathMatchers(HttpMethod.POST, "/auth").permitAll()   // sign-up
                        .anyExchange().authenticated())
                .oauth2ResourceServer(o -> o.jwt(jwt -> {
                }))
                .build();
    }

    /** Rate-limit bucket per user: the token's subject (Keycloak user id). */
    @Bean
    @Primary
    public KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .switchIfEmpty(Mono.just("anonymous"));
    }

    /** Rate-limit bucket per client IP, for the public sign-up endpoint. */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                .map(InetSocketAddress::getAddress)
                .map(a -> a.getHostAddress())
                .orElse("unknown"));
    }
}
