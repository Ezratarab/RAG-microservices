package com.example.billing_service.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "BILLING_USER_USAGE")
public class UserUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Keycloak user id (JWT "sub")
    @Column(nullable = false, unique = true, length = 36)
    private String customerId;

    private double amount;
}
