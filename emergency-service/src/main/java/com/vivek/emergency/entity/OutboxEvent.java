package com.vivek.emergency.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_events", indexes = {
    @Index(name = "idx_outbox_unpublished", columnList = "published, createdAt")
})
@Data
@NoArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 50)
    private String aggregateId;

    @Column(name = "kafka_topic", nullable = false, length = 100)
    private String kafkaTopic;

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(name = "published", nullable = false)
    private boolean published = false;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "last_retry_at")
    private LocalDateTime lastRetryAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public static OutboxEvent of(String aggregateType, String aggregateId,
                                  String kafkaTopic, String payload) {
        OutboxEvent e = new OutboxEvent();
        e.aggregateType = aggregateType;
        e.aggregateId = aggregateId;
        e.kafkaTopic = kafkaTopic;
        e.payload = payload;
        return e;
    }
}
