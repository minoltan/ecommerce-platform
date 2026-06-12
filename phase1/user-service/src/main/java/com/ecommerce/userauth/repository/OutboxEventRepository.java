package com.ecommerce.userauth.repository;

import com.ecommerce.userauth.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /** Polled by {@link com.ecommerce.userauth.outbox.OutboxRelay} (idx_outbox_unpublished). */
    List<OutboxEvent> findTop100ByPublishedFalseOrderByCreatedAtAsc();
}
