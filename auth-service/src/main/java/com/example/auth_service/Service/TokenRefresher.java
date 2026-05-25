package com.example.auth_service.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenRefresher {

    private final KeycloakService keycloakService;
    private final TokenService tokenService;

    @Value("${jwt.auth.converter.resource-id}")
    private String clientId;

    @Scheduled(fixedRate = 4 * 60 * 1000)
    public void refresh() {
        String token = keycloakService.getClientTokenFromKeycloak().block();
        tokenService.saveToken(clientId, token);
    }
}
