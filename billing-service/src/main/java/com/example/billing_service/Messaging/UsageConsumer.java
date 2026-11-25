package com.example.billing_service.Messaging;

import com.example.billing_service.Service.BillingService;
import com.example.common.events.Topics;
import com.example.common.events.UsageEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Usage metering is asynchronous: rag-service publishes, billing consumes. If billing is
 * down, events wait in Kafka and are billed when it comes back; answers are never blocked.
 */
@Component
@RequiredArgsConstructor
public class UsageConsumer {

    private final BillingService billingService;

    @KafkaListener(topics = Topics.BILLING_USAGE, groupId = "billing")
    public void onUsage(UsageEvent event) {
        billingService.recordUsage(event);
    }
}
