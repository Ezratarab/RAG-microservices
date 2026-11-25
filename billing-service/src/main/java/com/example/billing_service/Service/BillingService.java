package com.example.billing_service.Service;

import com.example.billing_service.Constants.PriceList;
import com.example.billing_service.DTO.PaymentDTO;
import com.example.billing_service.DTO.UserUsageDTO;
import com.example.billing_service.Entity.Payment;
import com.example.billing_service.Entity.ProcessedEvent;
import com.example.billing_service.Entity.UserUsage;
import com.example.billing_service.Repository.PaymentRepository;
import com.example.billing_service.Repository.ProcessedEventRepository;
import com.example.billing_service.Repository.UserUsageRepository;
import com.example.common.events.UsageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

@RequiredArgsConstructor
@Slf4j
@Service
public class BillingService {
    private final PaymentRepository paymentRepository;
    private final UserUsageRepository userUsageRepository;
    private final ProcessedEventRepository processedEventRepository;

    @Transactional
    public Long payBill(String customerId, PaymentDTO paymentDTO) {
        Payment payment = Payment.builder()
                .customerId(customerId)
                .billPrice(paymentDTO.getBillPrice())
                .paymentToken(paymentDTO.getPaymentToken())
                .build();
        paymentRepository.save(payment);
        log.info("Payment {} recorded for customer {}", payment.getId(), customerId);
        return payment.getId();
    }

    /**
     * Bills one usage event exactly once. The processed-event row and the usage update
     * commit together, so a redelivered event is skipped and never double-charged.
     */
    @Transactional
    public void recordUsage(UsageEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.debug("Usage event {} already processed, skipping", event.eventId());
            return;
        }
        BigDecimal price = PriceList.PRICES.get(event.type().toUpperCase(Locale.ROOT));
        if (price == null) {
            log.warn("Unknown usage type {} in event {}, not billed", event.type(), event.eventId());
        } else {
            UserUsage usage = findOrCreateUsage(event.userId());
            usage.setAmount(usage.getAmount().add(price));
            userUsageRepository.save(usage);
        }
        processedEventRepository.save(new ProcessedEvent(event.eventId(), Instant.now()));
    }

    /** Idempotent: a caller can only ever have one usage record. */
    @Transactional
    public UserUsageDTO addNewCustomer(String customerId) {
        UserUsage usage = findOrCreateUsage(customerId);
        return UserUsageDTO.builder()
                .userId(usage.getCustomerId())
                .amount(usage.getAmount())
                .build();
    }

    private UserUsage findOrCreateUsage(String customerId) {
        UserUsage usage = userUsageRepository.findByCustomerId(customerId);
        if (usage == null) {
            usage = userUsageRepository.save(UserUsage.builder().customerId(customerId).build());
        }
        return usage;
    }
}
