package com.example.billing_service.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Ids of usage events already billed. Kafka can deliver an event twice; this makes the second one a no-op. */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "PROCESSED_EVENT")
public class ProcessedEvent {

    @Id
    @Column(length = 36)
    private String eventId;

    @Column(nullable = false)
    private Instant processedAt;
}
