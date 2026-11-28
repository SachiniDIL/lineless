package com.lineless.queue_service.repository;

import com.lineless.queue_service.config.AbstractRepositoryTest;
import com.lineless.queue_service.entity.OutboxEvent;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.node.JsonNodeFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldFindUnpublishedEventsInCreationTimeOrder() {

        OutboxEvent laterUnpublishedEvent = createEvent(null);
        OutboxEvent publishedEvent = createEvent(
                Instant.parse("2026-01-01T10:10:00Z")
        );
        OutboxEvent earlierUnpublishedEvent = createEvent(null);

        Instant earlierCreatedAt = Instant.parse("2026-01-01T10:00:00Z");
        Instant laterCreatedAt = Instant.parse("2026-01-01T10:05:00Z");
        setCreatedAt(earlierUnpublishedEvent, earlierCreatedAt);
        setCreatedAt(laterUnpublishedEvent, laterCreatedAt);
        setCreatedAt(publishedEvent, Instant.parse("2026-01-01T09:55:00Z"));
        entityManager.clear();

        List<OutboxEvent> unpublishedEvents = outboxEventRepository
                .findByPublishedAtIsNullOrderByCreatedAtAsc();

        assertThat(unpublishedEvents)
                .extracting(OutboxEvent::getId)
                .containsExactly(
                        earlierUnpublishedEvent.getId(),
                        laterUnpublishedEvent.getId()
                );
        assertThat(unpublishedEvents)
                .extracting(OutboxEvent::getPublishedAt)
                .containsOnlyNulls();
        assertThat(unpublishedEvents)
                .extracting(OutboxEvent::getCreatedAt)
                .containsExactly(earlierCreatedAt, laterCreatedAt);
    }

    private OutboxEvent createEvent(Instant publishedAt) {
        OutboxEvent event = BeanUtils.instantiateClass(OutboxEvent.class);

        event.setAggregateType("QueueEntry");
        event.setAggregateId(UUID.randomUUID());
        event.setEventType("QueueEntryCreated");
        event.setPayload(JsonNodeFactory.instance.objectNode());
        event.setPublishedAt(publishedAt);

        return outboxEventRepository.saveAndFlush(event);
    }

    private void setCreatedAt(OutboxEvent event, Instant createdAt) {
        jdbcTemplate.update(
                "UPDATE outbox_events SET created_at = ? WHERE id = ?",
                Timestamp.from(createdAt),
                event.getId()
        );
    }
}
