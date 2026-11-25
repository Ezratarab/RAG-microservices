package com.example.auth_service.Entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Our own record of a user. Identity and profile (username, email, phone, password)
 * live only in Keycloak; this table keeps the link plus any app-specific data we add later.
 */
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "AUTH_USERS")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Keycloak user id = the "sub" claim in this user's JWTs. */
    @Column(name = "KEYCLOAK_ID", unique = true, nullable = false, length = 36)
    private String keycloakId;
}
