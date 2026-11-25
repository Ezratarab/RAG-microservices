package com.example.auth_service.Service;

import com.example.auth_service.DTO.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Keycloak Admin API calls. Keycloak is the single owner of user identity and credentials. */
@Slf4j
@Service
public class KeycloakService {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final ParameterizedTypeReference<List<Map<String, Object>>> USER_LIST =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient webClient;
    private final String realm;
    private final String clientUuid;

    public KeycloakService(@Qualifier("keycloakAdminWebClient") WebClient webClient,
                           @Value("${keycloak.realm}") String realm,
                           @Value("${keycloak.client.uuid}") String clientUuid) {
        this.webClient = webClient;
        this.realm = realm;
        this.clientUuid = clientUuid;
    }

    /** Creates the user and returns the Keycloak user id. */
    public String createUser(UserDTO user) {
        Map<String, Object> body = Map.of(
                "username", user.getUsername(),
                "email", user.getEmail(),
                "enabled", true,
                "credentials", List.of(Map.of(
                        "type", "password",
                        "value", user.getPassword(),
                        "temporary", false)),
                "attributes", Map.of("phone", List.of(user.getPhone())));

        return webClient.post()
                .uri("/admin/realms/{realm}/users", realm)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        String location = response.headers().asHttpHeaders().getFirst("Location");
                        if (location == null || location.isBlank()) {
                            return Mono.error(new IllegalStateException("Keycloak returned no Location header"));
                        }
                        return Mono.just(location.substring(location.lastIndexOf('/') + 1));
                    }
                    return response.bodyToMono(String.class).defaultIfEmpty("")
                            .flatMap(err -> Mono.error(new KeycloakException(
                                    "create user failed: " + response.statusCode() + " " + err)));
                })
                .timeout(TIMEOUT)
                .block();
    }

    /** Gives the user the client_user role of our client. */
    public void assignClientUserRole(String userId) {
        Map<String, Object> role = webClient.get()
                .uri("/admin/realms/{realm}/clients/{client}/roles/client_user", realm, clientUuid)
                .retrieve()
                .onStatus(HttpStatusCode::isError, r -> Mono.error(new KeycloakException("role lookup failed: " + r.statusCode())))
                .bodyToMono(JSON_OBJECT)
                .timeout(TIMEOUT)
                .block();

        webClient.post()
                .uri("/admin/realms/{realm}/users/{user}/role-mappings/clients/{client}", realm, userId, clientUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(List.of(role))
                .retrieve()
                .onStatus(HttpStatusCode::isError, r -> Mono.error(new KeycloakException("role mapping failed: " + r.statusCode())))
                .toBodilessEntity()
                .timeout(TIMEOUT)
                .block();
    }

    /** Compensation step: removes a user created earlier in a sign-up that later failed. */
    public void deleteUser(String userId) {
        webClient.delete()
                .uri("/admin/realms/{realm}/users/{user}", realm, userId)
                .retrieve()
                .toBodilessEntity()
                .timeout(TIMEOUT)
                .block();
    }

    @SuppressWarnings("unchecked")
    public List<UserDTO> getAllUsers() {
        List<Map<String, Object>> users = webClient.get()
                .uri("/admin/realms/{realm}/users?first=0&max=1000", realm)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, r -> Mono.error(new KeycloakException("list users failed: " + r.statusCode())))
                .bodyToMono(USER_LIST)
                .timeout(TIMEOUT)
                .block();

        return users == null ? List.of() : users.stream().map(u -> {
            Map<String, List<String>> attrs = (Map<String, List<String>>) u.get("attributes");
            List<String> phone = attrs == null ? null : attrs.get("phone");
            return UserDTO.builder()
                    .id((String) u.get("id"))
                    .username((String) u.get("username"))
                    .email((String) u.get("email"))
                    .phone(phone == null || phone.isEmpty() ? null : phone.get(0))
                    .build();
        }).toList();
    }

    public static class KeycloakException extends RuntimeException {
        public KeycloakException(String message) {
            super(message);
        }
    }
}
