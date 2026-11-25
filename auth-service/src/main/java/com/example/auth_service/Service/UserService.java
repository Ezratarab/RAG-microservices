package com.example.auth_service.Service;

import com.example.auth_service.DTO.UserDTO;
import com.example.auth_service.Entity.User;
import com.example.auth_service.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final KeycloakService keycloakService;

    /**
     * Sign-up spans two systems (Keycloak + our DB), which can't share one transaction.
     * Saga with compensation: if anything after the Keycloak user is created fails,
     * delete that Keycloak user so no half-created account is left behind.
     */
    @Transactional
    public String createUser(UserDTO request) {
        log.info("Creating user {}", request.getUsername());
        String keycloakId = keycloakService.createUser(request);
        try {
            keycloakService.assignClientUserRole(keycloakId);
            userRepository.save(User.builder().keycloakId(keycloakId).build());
        } catch (RuntimeException e) {
            log.warn("Sign-up of {} failed after Keycloak user {} was created; rolling back", request.getUsername(), keycloakId, e);
            try {
                keycloakService.deleteUser(keycloakId);
            } catch (RuntimeException cleanup) {
                log.error("Compensation failed: Keycloak user {} must be removed manually", keycloakId, cleanup);
            }
            throw e;
        }
        log.info("User {} created with Keycloak id {}", request.getUsername(), keycloakId);
        return keycloakId;
    }

    /** Keycloak is the source of truth for profile data, so read it from there. */
    public List<UserDTO> getAllUsers() {
        return keycloakService.getAllUsers();
    }
}
