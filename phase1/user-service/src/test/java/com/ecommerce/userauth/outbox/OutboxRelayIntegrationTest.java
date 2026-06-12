package com.ecommerce.userauth.outbox;

import com.ecommerce.userauth.AbstractIntegrationTest;
import com.ecommerce.userauth.domain.OutboxEvent;
import com.ecommerce.userauth.repository.OutboxEventRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class OutboxRelayIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private Consumer<String, String> consumer;

    @AfterEach
    void closeConsumer() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    void publishesUnpublishedEventToMappedTopicAndMarksItPublished() {
        UUID userId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        String payload = """
                {"eventId":"%s","eventType":"UserRegistered","occurredAt":"2026-06-12T00:00:00Z","correlationId":"%s","schemaVersion":1,"data":{"userId":"%s","email":"relay@example.com"}}
                """.formatted(UUID.randomUUID(), correlationId, userId).strip();

        OutboxEvent event = new OutboxEvent(userId, "UserRegistered", payload, correlationId);
        outboxEventRepository.save(event);

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("test-outbox-relay", "true", KAFKA.getBootstrapServers());
        consumerProps.put("key.deserializer", StringDeserializer.class.getName());
        consumerProps.put("value.deserializer", StringDeserializer.class.getName());
        consumerProps.put("auto.offset.reset", "earliest");
        consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(consumerProps);
        consumer.subscribe(java.util.List.of("user-auth.user.registered"));

        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
        ConsumerRecord<String, String> record = records.iterator().next();

        assertThat(record.key()).isEqualTo(userId.toString());
        assertThat(record.value()).isEqualTo(payload);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            OutboxEvent reloaded = outboxEventRepository.findById(event.getId()).orElseThrow();
            assertThat(reloaded.isPublished()).isTrue();
            assertThat(reloaded.getPublishedAt()).isNotNull();
        });
    }
}
