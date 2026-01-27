package com.example.billing_service.Controller;

import com.example.billing_service.DTO.PaymentDTO;
import com.example.billing_service.DTO.UserUsageDTO;
import com.example.billing_service.Service.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/billing")
public class BillingController {

    private final BillingService billingService;

    @PostMapping("/newCustomer")
    @ResponseStatus(HttpStatus.CREATED)
    public UserUsageDTO addNewCustomer(@RequestBody Long newCustomerId) {
        return billingService.addNewCustomer(newCustomerId);
    }

    @PostMapping("/pay")
    @ResponseStatus(HttpStatus.CREATED)
    public Long payBill(@RequestBody PaymentDTO paymentDTO) {
        return billingService.payBill(paymentDTO);
    }
//
//    @PostMapping("/addUsage")
//    @ResponseStatus(HttpStatus.OK)
//    public UserUsageDTO addUsage(@RequestBody String transactionType) {
//
//    }
}
