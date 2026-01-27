package com.example.auth_service.Repository;

import com.example.auth_service.Entity.SecretToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecretTokenRepository extends JpaRepository<SecretToken, Long> {
    Integer countByUsername(String username);

    SecretToken findSecretTokenByUsername(String username);

    void deleteSecretTokenByUsername(String username);
}
