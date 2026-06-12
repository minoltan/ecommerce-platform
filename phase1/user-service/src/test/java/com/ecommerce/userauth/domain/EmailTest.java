package com.ecommerce.userauth.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailTest {

    @Test
    void lowercasesAndTrimsOnConstruction() {
        Email email = new Email("  John.Doe@Example.COM ");

        assertThat(email.value()).isEqualTo("john.doe@example.com");
    }

    @Test
    void rejectsInvalidFormat() {
        assertThatThrownBy(() -> new Email("not-an-email"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> new Email(null))
                .isInstanceOf(NullPointerException.class);
    }
}
