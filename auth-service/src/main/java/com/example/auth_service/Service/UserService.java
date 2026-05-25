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
    private final TokenService tokenService;
    private final KeycloakService keycloakService;
    private WebClient webClient;
    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostConstruct
    public void init() {
        webClient = keycloakService.webClient;
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
        User keycloakUser = user;
        keycloakUser.setPassword(userRequest.getPassword());
        String userUUID = keycloakService.createUserInKeycloak(keycloakUser).block();
        user.setUUID(userUUID);
        System.out.println(user);
        userRepository.save(user);
        keycloakService.assignClientRole(userUUID, keycloakService.client_uuid).block();
        log.info("User {} is saved", user.getUsername());
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


}
