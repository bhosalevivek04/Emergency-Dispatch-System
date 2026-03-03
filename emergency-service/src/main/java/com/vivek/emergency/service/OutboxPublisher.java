package com.vivek.emergency.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivek.emergency.entity.OutboxEvent;
import com.vivek.emergency.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private static final int MAX_RETRIES = 5;

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;  // Add ObjectMapper for deserialization
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelay = 1000) // poll every second
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxRepository.findUnpublishedEvents();
        if (pending.isEmpty()) return;

        log.debug("Outbox: processing {} pending events", pending.size());

        for (OutboxEvent event : pending) {
            try {
                // Deserialize the JSON string back to an object before sending to Kafka
                // This prevents double-serialization
                Object eventObject = objectMapper.readValue(event.getPayload(), Object.class);
                
                kafkaTemplate
                    .send(event.getKafkaTopic(), event.getAggregateId(), eventObject)
                    .get(5, TimeUnit.SECONDS);

                event.setPublished(true);
                event.setPublishedAt(LocalDateTime.now());
                outboxRepository.save(event);

                meterRegistry.counter("outbox.events.published.total").increment();
                log.info("Outbox published: {} {} -> {}", event.getAggregateType(),
                        event.getAggregateId(), event.getKafkaTopic());

            } catch (Exception e) {
                int retries = event.getRetryCount() + 1;
                event.setRetryCount(retries);
                event.setLastRetryAt(LocalDateTime.now());
                event.setErrorMessage(e.getMessage());
                outboxRepository.save(event);

                meterRegistry.counter("outbox.events.failed.total").increment();
                log.error("Outbox publish failed (attempt {}/{}): {} {}",
                        retries, MAX_RETRIES, event.getAggregateType(), event.getAggregateId(), e);

                if (retries >= MAX_RETRIES) {
                    meterRegistry.counter("outbox.events.exhausted.total").increment();
                    log.error("OUTBOX MAX RETRIES EXCEEDED — manual intervention required for outbox id={}  emergencyId={}",
                            event.getId(), event.getAggregateId());
                }
            }
        }

        meterRegistry.gauge("outbox.events.pending.count", outboxRepository.countUnpublished());
    }

    @Scheduled(cron = "0 0 3 * * ?") // daily at 3 AM
    @Transactional
    public void cleanupPublishedEvents() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);
        int deleted = outboxRepository.deleteOldPublishedEvents(threshold);
        log.info("Outbox cleanup: deleted {} old published events", deleted);
    }
}
