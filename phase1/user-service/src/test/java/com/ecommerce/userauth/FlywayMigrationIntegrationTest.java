package com.ecommerce.userauth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void v1MigrationCreatesUserDbSchema() {
        Integer tableCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE()
                AND table_name IN ('users', 'user_addresses', 'email_verifications',
                                    'password_reset_tokens', 'user_auth_outbox')
                """,
                Integer.class);

        assertThat(tableCount).isEqualTo(5);
    }

    @Test
    void usersTableEnforcesUniqueEmail() {
        Integer uniqueConstraintCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_schema = DATABASE()
                AND table_name = 'users'
                AND constraint_name = 'uq_users_email'
                AND constraint_type = 'UNIQUE'
                """,
                Integer.class);

        assertThat(uniqueConstraintCount).isEqualTo(1);
    }
}
