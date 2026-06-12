package com.ecommerce.userauth.outbox;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxTopicsTest {

    @Test
    void mapsKnownEventTypesToTheirLldTopics() {
        assertThat(OutboxTopics.topicFor("UserRegistered")).isEqualTo("user-auth.user.registered");
        assertThat(OutboxTopics.topicFor("UserLoggedIn")).isEqualTo("user-auth.user.logged-in");
        assertThat(OutboxTopics.topicFor("UserDeactivated")).isEqualTo("user-auth.user.deactivated");
        assertThat(OutboxTopics.topicFor("PasswordResetRequested")).isEqualTo("user-auth.user.password-reset-requested");
    }

    @Test
    void rejectsUnknownEventTypes() {
        assertThatThrownBy(() -> OutboxTopics.topicFor("SomethingElse"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
