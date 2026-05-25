package com.example.auth_service.Service;

import com.example.auth_service.DTO.UserDTO;
import com.example.auth_service.Entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
public class KeycloakService {

    public final WebClient webClient;

    @Value("${jwt.auth.client_secret}")
    private String client_secret;

    @Value("${jwt.auth.converter.resource-id}")
    public String client_id;

    @Value("${keycloak.client.uuid}")
    public String client_uuid;

    @Value("${keycloak.port}")
    private String keycloak_port;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.port}")
    private String port;

    private String baseUrl;
    private TokenService tokenService;

    public KeycloakService(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
                           TokenService tokenService) {
        this.webClient = WebClient.builder().build();
        this.baseUrl = issuerUri;
        this.tokenService = tokenService;
    }

    public Mono<String> getClientTokenFromKeycloak() {
        return webClient.post()
                .uri(baseUrl + "/protocol/openid-connect/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(BodyInserters
                        .fromFormData("grant_type", "client_credentials")
                        .with("client_id", client_id)
                        .with("client_secret", client_secret))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> {
                    return response.bodyToMono(String.class)
                            .flatMap(errorBody -> Mono.error(new RuntimeException("KEYCLOAK Error getClientTokenFromKeycloak: " + response.statusCode() + " - " + errorBody)));
                })
                .bodyToMono(Map.class)
                .map(map -> (String) map.get("access_token"));
    }

    public Mono<Void> assignClientRole(String userId, String clientUuid) {
        String token = tokenService.getClientToken(client_id);

        return webClient.get()
                .uri("http://localhost:" + port + "/admin/realms/" + realm +
                        "/clients/" + clientUuid + "/roles/client_user")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(role -> webClient.post()
                        .uri("http://localhost:" + port + "/admin/realms/" + realm +
                                "/users/" + userId + "/role-mappings/clients/" + clientUuid)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(List.of(role))
                        .retrieve()
                        .bodyToMono(Void.class));
    }

    public Mono<String> createUserInKeycloak(User user) {
        String url = "http://localhost:" + port + "/admin/realms/" + realm + "/users";
        String clientToken = tokenService.getClientToken(client_id);
        Map<String, Object> body = Map.of(
                "username", user.getUsername(),
                "enabled", true,
                "email", user.getEmail(),
                "credentials", List.of(
                        Map.of(
                                "type", "password",
                                "value", user.getPassword(),
                                "temporary", false
                        )
                ),
                "attributes", Map.of(
                        "customer-id", List.of(user.getId() != null ? user.getId().toString() : "0"),
                        "phone", List.of(user.getPhone() != null ? user.getPhone() : "")
                )
        );
        System.out.println(body);
        return webClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + clientToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful() || response.statusCode().value() == 201) {
                        String location = response.headers().asHttpHeaders().getFirst("Location");
                        if (location == null || location.isEmpty()) {
                            return Mono.error(new RuntimeException("No Location header returned, cannot get user ID"));
                        }
                        String userId = location.substring(location.lastIndexOf("/") + 1);
                        System.out.println("User created with Keycloak ID: " + userId);
                        return Mono.just(userId);
                    } else {
                        return response.bodyToMono(String.class)
                                .flatMap(error -> Mono.error(new RuntimeException(
                                        "KEYCLOAK Error createUserInKeycloak: " + response.statusCode() + " - " + error
                                )));
                    }
                });
    }

    public List<UserDTO> getAllUsersFromKeyCloak() {
        String url = "http://localhost:" + port + "/admin/realms/" + realm + "/users?first=0&max=10000";
        String clientToken = tokenService.getClientToken(client_id);

        return webClient.get()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + clientToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> {
                    return response.bodyToMono(String.class)
                            .flatMap(errorBody -> Mono.error(new RuntimeException("KEYCLOAK Error getAllUsersFromKeyCloak: " + response.statusCode() + " - " + errorBody)));
                })
                .bodyToFlux(Map.class)
                .map(userMap -> {
                    UserDTO dto = new UserDTO();
                    dto.setUsername((String) userMap.get("username"));
                    dto.setEmail((String) userMap.get("email"));
                    Map<String, List<String>> attrs = (Map<String, List<String>>) userMap.get("attributes");
                    if (attrs != null) {
                        dto.setPhone(attrs.get("phone") != null ? attrs.get("phone").get(0) : null);
                        dto.setCustomerId(attrs.get("customer-id") != null ?
                                Long.parseLong(attrs.get("customer-id").get(0)) :
                                null);
                    }
                    return dto;
                })
                .collectList()
                .block();
    }

}