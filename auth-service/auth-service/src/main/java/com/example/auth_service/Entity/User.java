package com.example.auth_service.Entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import lombok.*;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "AUTH_USERS")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String UUID;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false, length = 20)
    private String email;

    @Column(nullable = false)
    private String password;
}
