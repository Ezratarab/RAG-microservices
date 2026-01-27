package com.example.auth_service.Security;

import com.example.auth_service.Entity.SecretToken;
import com.example.auth_service.Repository.SecretTokenRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Profile(Constants.PROFILE_PRODUCTION)
@Configuration
public class SecurityConfig {

    private final JWTConverter jwtConverter;
    private final SecretTokenRepository secretTokenRepository;

    private String baseUrl;

    public SecurityConfig(JWTConverter jwtConverter, SecretTokenRepository secretTokenRepository, @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri) {
        this.jwtConverter = jwtConverter;
        this.secretTokenRepository = secretTokenRepository;
        this.baseUrl = issuerUri;
    }

    @Value("${jwt.auth.client_secret}")
    private String client_secret;

    @Value("${jwt.auth.converter.resource-id}")
    private String client_id;

    @Value("${keycloak.client.uuid}")
    public String client_uuid;

    @Value("${keycloak.port}")
    private String keycloak_port;

    @Value("${keycloak.realm}")
    private String realm;


    public final WebClient keycloakWebClient = WebClient.builder().baseUrl(baseUrl).build();
    public final WebClient webClient = WebClient.builder().build();

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/").permitAll();
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 ->
                        oauth2
                                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)))
                .sessionManagement(sessionManagement -> sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    private Mono<String> getClientTokenFromKeycloak() {
        System.out.println("**************************************************");
        String grant_type = "client_credentials";
        return keycloakWebClient.post()
                .uri(baseUrl + "/protocol/openid-connect/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(BodyInserters
                        .fromFormData("grant_type", grant_type)
                        .with("client_id", client_id)
                        .with("client_secret", client_secret)
                )
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> {
                    return response.bodyToMono(String.class)
                            .flatMap(errorBody -> Mono.error(new RuntimeException("KEYCLOAK Error getClientTokenFromKeycloak: " + response.statusCode() + " - " + errorBody)));
                })
                .bodyToMono(Map.class)
                .map(map -> (String) map.get("access_token"));
    }


    private void storeClientToken(String token) {
        SecretToken secretToken = SecretToken.builder()
                .token(token)
                .username(client_id)
                .build();
        secretTokenRepository.save(secretToken);
    }

    public String getClientToken() {
        SecretToken secretToken = secretTokenRepository.findSecretTokenByUsername(client_id);
        return secretToken.getToken();
    }

    private void deleteClientTokens() {
        secretTokenRepository.deleteSecretTokenByUsername(client_id);
    }

    private boolean checkCountClientToken(String username) {
        return secretTokenRepository.countByUsername(username) == 1;
    }

    private void generateNewClientSecret(String adminToken) {
        String clientToken = getClientToken();
        String newClientSecret = webClient.post()
                .uri("https://localhost:" + keycloak_port +
                        "/admin/realms/" + realm +
                        "/clients/" + client_uuid +
                        "/client-secret")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + clientToken)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new RuntimeException("Keycloak error generateNewClientSecret: " + response.statusCode() + " - " + body)
                                ))
                )
                .bodyToMono(Map.class)
                .map(map -> (String) map.get("value"))
                .block();
    }

    @Scheduled(fixedRate = 4 * 60 * 1000)
    public void refreshClientToken() {
        deleteClientTokens();
        String token = getClientTokenFromKeycloak().block();
        storeClientToken(token);
    }

    @PostConstruct
    public void init() {
        deleteClientTokens();
        String token = getClientTokenFromKeycloak().block();
        storeClientToken(token);
        if (!checkCountClientToken(client_id)) {
            throw new IllegalStateException(
                    "Token count check failed for : " + client_id
            );
        }
        System.out.println("Token: " + token);
    }

}
