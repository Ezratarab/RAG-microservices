package com.example.common.events;

import java.time.Instant;

/**
 * A billable action. {@code eventId} is unique per action so the consumer can ignore
 * duplicates (Kafka delivers at least once).
 */
public record UsageEvent(String eventId, String userId, String type, Instant occurredAt) {
}
