package com.example.billing_service.Controller;

import com.example.billing_service.DTO.PaymentDTO;
import com.example.billing_service.DTO.UserUsageDTO;
import com.example.billing_service.Service.BillingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/billing")
public class BillingController {

    private final BillingService billingService;

    // The customer is always the authenticated caller (JWT "sub" = Keycloak user id),
    // never a value taken from the request body.
    @PostMapping("/newCustomer")
    @ResponseStatus(HttpStatus.CREATED)
    public UserUsageDTO addNewCustomer(@AuthenticationPrincipal Jwt jwt) {
        return billingService.addNewCustomer(jwt.getSubject());
    }

    @PostMapping("/pay")
    @ResponseStatus(HttpStatus.CREATED)
    public Long payBill(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PaymentDTO paymentDTO) {
        return billingService.payBill(jwt.getSubject(), paymentDTO);
    }
}
