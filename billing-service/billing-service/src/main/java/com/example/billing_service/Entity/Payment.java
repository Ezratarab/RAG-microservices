package com.example.billing_service.Entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "BILLING_PAYMENT")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "payment_seq_gen")
    @SequenceGenerator(name = "payment_seq_gen", sequenceName = "PAYMENT_SEQ", allocationSize = 1)
    private Long id;


    @Column(nullable = false)
    //taken from user entity in auth_service
    private Long customerId;

    private int billPrice;

    @Column(nullable = false, length = 16)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String cardNumber;

    @Column(nullable = false, length = 70)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String cardHolder;

    @Column(nullable = false, length = 3)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Integer cvc;

}
