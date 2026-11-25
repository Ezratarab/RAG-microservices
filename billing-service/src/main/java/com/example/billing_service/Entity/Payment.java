package com.example.billing_service.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "BILLING_PAYMENT")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "payment_seq_gen")
    @SequenceGenerator(name = "payment_seq_gen", sequenceName = "PAYMENT_SEQ", allocationSize = 1)
    private Long id;

    // Keycloak user id (JWT "sub") of the paying customer
    @Column(nullable = false, length = 36)
    private String customerId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal billPrice;

    // Token/ID from the payment provider (e.g. Stripe PaymentMethod id).
    // Raw card number, holder and CVC must never reach or be stored by this service (PCI DSS).
    @Column(nullable = false, length = 255)
    private String paymentToken;
}
