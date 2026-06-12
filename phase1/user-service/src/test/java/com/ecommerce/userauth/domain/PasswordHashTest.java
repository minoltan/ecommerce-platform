package com.ecommerce.userauth.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordHashTest {

    @Test
    void hashedPasswordMatchesOriginalRawPassword() {
        PasswordHash hash = PasswordHash.hash("correct-horse-battery");

        assertThat(hash.matches("correct-horse-battery")).isTrue();
    }

    @Test
    void hashedPasswordDoesNotMatchWrongRawPassword() {
        PasswordHash hash = PasswordHash.hash("correct-horse-battery");

        assertThat(hash.matches("wrong-password")).isFalse();
    }

    @Test
    void rejectsPasswordShorterThanEightCharacters() {
        assertThatThrownBy(() -> PasswordHash.hash("short"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hashUsesBcryptCostOfAtLeastTwelve() {
        PasswordHash hash = PasswordHash.hash("correct-horse-battery");

        // bcrypt format: $2a$<cost>$<salt+hash>
        String cost = hash.hash().split("\\$")[2];
        assertThat(Integer.parseInt(cost)).isGreaterThanOrEqualTo(12);
    }
}
