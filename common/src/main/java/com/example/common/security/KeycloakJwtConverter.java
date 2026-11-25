package com.example.common.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Maps a Keycloak JWT to Spring authorities: scopes plus the client roles found under
 * {@code resource_access.<resourceId>.roles}, each prefixed with {@code ROLE_}.
 * Shared by every service so a fix here applies everywhere (previously copied 3 times).
 */
public class KeycloakJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String ROLE_PREFIX = "ROLE_";

    private final JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
    private final String principalAttribute;
    private final String resourceId;

    public KeycloakJwtConverter(String principalAttribute, String resourceId) {
        this.principalAttribute = principalAttribute;
        this.resourceId = resourceId;
    }

    @Override
    public AbstractAuthenticationToken convert(@NonNull Jwt jwt) {
        Collection<GrantedAuthority> authorities = Stream.concat(
                scopes.convert(jwt).stream(),
                clientRoles(jwt).stream()
        ).collect(Collectors.toSet());
        return new JwtAuthenticationToken(jwt, authorities, principalName(jwt));
    }

    private String principalName(Jwt jwt) {
        Object value = jwt.getClaims().get(principalAttribute);
        return value != null ? value.toString() : jwt.getClaimAsString(JwtClaimNames.SUB);
    }

    @SuppressWarnings("unchecked")
    private Collection<? extends GrantedAuthority> clientRoles(Jwt jwt) {
        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess == null || !(resourceAccess.get(resourceId) instanceof Map<?, ?> resource)) {
            return Set.of();
        }
        if (!(resource.get("roles") instanceof Collection<?> roles)) {
            return Set.of();
        }
        return ((Collection<String>) roles).stream()
                .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role))
                .collect(Collectors.toSet());
    }
}
