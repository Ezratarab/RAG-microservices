package com.example.billing_service.Service;

import com.example.billing_service.Constants.PriceList;
import com.example.billing_service.DTO.PaymentDTO;
import com.example.billing_service.DTO.UserUsageDTO;
import com.example.billing_service.Entity.Payment;
import com.example.billing_service.Entity.UserUsage;
import com.example.billing_service.Repository.PaymentRepository;
import com.example.billing_service.Repository.UserUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Slf4j
@Service
public class BillingService {
    private final PaymentRepository paymentRepository;
    private final UserUsageRepository userUsageRepository;


    public Long payBill(PaymentDTO paymentDTO) {
        Payment payment = Payment.builder()
                //.customerId(paymentDTO.getCustomerId()) לעשות שיעבוד עם KEYCLOCK
                .billPrice(paymentDTO.getBillPrice())
                .cardNumber(paymentDTO.getCardNumber())
                .cardHolder(paymentDTO.getCardHolder())
                .cvc(paymentDTO.getCvc())
                .build();
        paymentRepository.save(payment);
        log.info("BILL {} was payed by {}", payment.getId(), payment.getCustomerId());
        return payment.getId();
    }

    private UserUsage addUsage(Long customerId, String transactionType) {
        UserUsage userUsage = userUsageRepository.findByCustomerId(customerId);
        if (userUsage == null) {
            userUsage = createUsage(customerId);
        }
        String apiName = transactionType.toUpperCase();
        Double price = PriceList.PRICES.get(apiName);
        userUsage.setAmount(userUsage.getAmount() + price);
        userUsageRepository.save(userUsage);
        return userUsage;
    }

    private UserUsage createUsage(Long userId) {
        UserUsage userUsage = new UserUsage();
        userUsage.setCustomerId(userId);
        userUsageRepository.save(userUsage);
        return userUsage;
    }

    public UserUsageDTO addNewCustomer(Long newCustomerId) {
        UserUsage newUserUsage = UserUsage.builder()
                .customerId(newCustomerId)
                .amount(0)
                .build();
        userUsageRepository.save(newUserUsage);
        UserUsageDTO userUsageDTO = UserUsageDTO.builder()
                .userId(newUserUsage.getCustomerId())
                .amount(newUserUsage.getAmount())
                .build();
        return userUsageDTO;
    }
}
