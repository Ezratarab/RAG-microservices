package com.example.auth_service.Security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient for the Keycloak Admin API. Spring gets the client-credentials token,
 * keeps it in memory and refreshes it before it expires: no token table, no scheduler,
 * nothing written to logs or the database.
 */
@Configuration
public class KeycloakAdminClientConfig {

    public static final String REGISTRATION_ID = "keycloak-admin";

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientService clientService) {
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clientService);
        manager.setAuthorizedClientProvider(
                OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
        return manager;
    }

    @Bean
    public WebClient keycloakAdminWebClient(
            OAuth2AuthorizedClientManager manager,
            @Value("${keycloak.base-url}") String keycloakBaseUrl) {
        var oauth = new ServletOAuth2AuthorizedClientExchangeFilterFunction(manager);
        oauth.setDefaultClientRegistrationId(REGISTRATION_ID);
        return WebClient.builder()
                .baseUrl(keycloakBaseUrl)
                .apply(oauth.oauth2Configuration())
                .build();
    }
}
