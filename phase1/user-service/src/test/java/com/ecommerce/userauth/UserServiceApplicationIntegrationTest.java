package com.ecommerce.userauth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserServiceApplicationIntegrationTest extends AbstractIntegrationTest {

    @Test
    void contextLoadsWithMysqlKafkaAndRedisContainers() {
        assertThat(MYSQL.isRunning()).isTrue();
        assertThat(KAFKA.isRunning()).isTrue();
        assertThat(REDIS.isRunning()).isTrue();
    }
}
