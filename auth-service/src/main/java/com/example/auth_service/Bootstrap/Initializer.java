package com.example.auth_service.Bootstrap;

import com.example.auth_service.Service.KeycloakService;
import com.example.auth_service.Service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class Initializer {

    private final KeycloakService keycloakService;
    private final TokenService tokenService;

    @Value("${jwt.auth.converter.resource-id}")
    private String client_id;

    public Initializer(KeycloakService keycloakService, TokenService tokenService) {
        this.keycloakService = keycloakService;
        this.tokenService = tokenService;
    }

    @Bean
    public ApplicationRunner init() {
        return args -> {

            tokenService.deleteTokens(client_id);

            String token = keycloakService
                    .getClientTokenFromKeycloak()
                    .block();

            tokenService.saveToken(client_id, token);

            System.out.println("Token: " + token);
        };
    }
}