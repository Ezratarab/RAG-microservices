package com.example.billing_service.DTO;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentDTO {

    // The paying customer comes from the caller's JWT, not from this DTO.
    private int billPrice;

    // Issued by the payment provider's client-side form; card details never pass through this API.
    private String paymentToken;
}
