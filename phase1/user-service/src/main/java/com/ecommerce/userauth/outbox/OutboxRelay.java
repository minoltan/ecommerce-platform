package com.ecommerce.userauth.outbox;

import com.ecommerce.userauth.domain.OutboxEvent;
import com.ecommerce.userauth.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Polls {@code user_auth_outbox} for unpublished rows and publishes each to its mapped Kafka
 * topic (LLD §11 — eventual consistency within ≤ 500 ms of the write-side transaction commit).
 *
 * <p>Each event is published keyed by {@code aggregateId} (the {@code userId}), per ADR-0002's
 * {@code userId} partition key for {@code user-auth.*} topics. The envelope stored in
 * {@code payload} is published verbatim — it already carries {@code eventId}, {@code eventType},
 * {@code occurredAt}, {@code correlationId}, and {@code schemaVersion}.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelay(OutboxEventRepository outboxEventRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.poll-interval-ms:500}")
    @Transactional
    public void publishUnpublishedEvents() {
        for (OutboxEvent event : outboxEventRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc()) {
            String topic = OutboxTopics.topicFor(event.getEventType());
            String key = event.getAggregateId().toString();
            try {
                kafkaTemplate.send(topic, key, event.getPayload()).get(5, TimeUnit.SECONDS);
                event.markPublished(Instant.now());
                outboxEventRepository.save(event);
            } catch (Exception e) {
                log.error("Failed to publish outbox event id={} type={} to topic={}",
                        event.getId(), event.getEventType(), topic, e);
            }
        }
    }
}
