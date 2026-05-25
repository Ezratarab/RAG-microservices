package com.example.auth_service.Service;

import com.example.auth_service.Entity.SecretToken;
import com.example.auth_service.Repository.SecretTokenRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final SecretTokenRepository repo;

    @Transactional
    public void deleteTokens(String clientId) {
        repo.deleteSecretTokenByUsername(clientId);
    }

    @Transactional
    public void saveToken(String clientId, String token) {
        SecretToken entity = SecretToken.builder()
                .username(clientId)
                .token(token)
                .build();

        repo.save(entity);
    }

    public String getClientToken(String clientId) {
        return repo.findSecretTokenByUsername(clientId).getToken();
    }
}