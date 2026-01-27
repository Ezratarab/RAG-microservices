package com.example.auth_service.Service;

import com.example.auth_service.DTO.UserDTO;
import com.example.auth_service.Entity.User;
import com.example.auth_service.Repository.UserRepository;
import com.example.auth_service.Security.SecurityConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j //for logs
public class UserService {

    private final SecurityConfig securityConfig;
    private final UserRepository userRepository;
    private WebClient webClient;
    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostConstruct
    public void init() {
        webClient = securityConfig.keycloakWebClient;
    }

    @Value("${keycloak.port}")
    private String port;
    @Value("${keycloak.realm}")
    private String realm;

    public void createUser(UserDTO userRequest) {
        System.out.println(userRequest);
        User user = User.builder()
                .phone(userRequest.getPhone())
                .username(userRequest.getUsername())
                .email(userRequest.getEmail())
                .password(passwordEncoder.encode(userRequest.getPassword()))
                .build();

        String userUUID = createUserInKeycloak(user).block();
        user.setUUID(userUUID);
        System.out.println(user);
        userRepository.save(user);
        assignClientRole(userUUID, securityConfig.client_uuid).block();
        log.info("User {} is saved", user.getUsername());
    }

    private Mono<String> createUserInKeycloak(User user) {
        String url = "http://localhost:" + port + "/admin/realms/" + realm + "/users";
        String clientToken = securityConfig.getClientToken();
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


    private Mono<Void> assignClientRole(String userId, String clientUuid) {
        String token = securityConfig.getClientToken();

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


    public List<UserDTO> getAllUsers() {
        List<User> users = userRepository.findAll();
        return users.stream().map(user -> mapToUserResonse(user)).toList();
    }

    private UserDTO mapToUserResonse(User user) {
        return UserDTO.builder()
                .customerId(user.getId())
                .username(user.getUsername())
                .phone(user.getPhone())
                .email(user.getEmail())
                .build();
    }

    public List<UserDTO> getAllUsersFromKeyCloak() {
        String url = "http://localhost:" + port + "/admin/realms/" + realm + "/users?first=0&max=10000";
        String clientToken = securityConfig.getClientToken();

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
