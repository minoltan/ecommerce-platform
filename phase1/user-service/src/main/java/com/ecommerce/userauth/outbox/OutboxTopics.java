package com.ecommerce.userauth.outbox;

import java.util.Map;

/**
 * Maps {@code user_auth_outbox.event_type} values to Kafka topics, per the
 * {@code {context}.{entity}.{event}} convention (ADR-0002 §1) and the published-events table
 * in {@code docs/lld/user-auth-lld.md} §9.1.
 */
public final class OutboxTopics {

    private static final Map<String, String> TOPICS_BY_EVENT_TYPE = Map.of(
            "UserRegistered", "user-auth.user.registered",
            "UserLoggedIn", "user-auth.user.logged-in",
            "UserDeactivated", "user-auth.user.deactivated",
            "PasswordResetRequested", "user-auth.user.password-reset-requested");

    private OutboxTopics() {
    }

    public static String topicFor(String eventType) {
        String topic = TOPICS_BY_EVENT_TYPE.get(eventType);
        if (topic == null) {
            throw new IllegalArgumentException("No Kafka topic mapped for outbox event type: " + eventType);
        }
        return topic;
    }
}
