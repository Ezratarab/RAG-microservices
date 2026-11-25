package com.example.rag_service.Billing;

import com.example.common.events.Topics;
import com.example.common.events.UsageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** Sends billable actions to billing-service. Fire-and-forget: billing being down never blocks answers. */
@Slf4j
@Component
@RequiredArgsConstructor
public class UsagePublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(String userId, String type) {
        UsageEvent event = new UsageEvent(UUID.randomUUID().toString(), userId, type, Instant.now());
        kafkaTemplate.send(Topics.BILLING_USAGE, userId, event)   // key = user: per-user ordering
                .whenComplete((ok, err) -> {
                    if (err != null) log.error("Usage event {} not sent", event.eventId(), err);
                });
    }
}
