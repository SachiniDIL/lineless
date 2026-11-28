package com.lineless.queue_service.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueueEnumsTest {

    @Test
    void shouldIncludeEnhancedSessionAndTicketEnumValues() {

        assertThat(QueueSessionStatus.values())
                .contains(
                        QueueSessionStatus.OPEN,
                        QueueSessionStatus.PAUSED,
                        QueueSessionStatus.CLOSED,
                        QueueSessionStatus.CANCELLED
                );

        assertThat(QueueEntryStatus.values())
                .contains(
                        QueueEntryStatus.WAITING,
                        QueueEntryStatus.CALLED,
                        QueueEntryStatus.COMPLETED,
                        QueueEntryStatus.SKIPPED,
                        QueueEntryStatus.LEFT,
                        QueueEntryStatus.CANCELLED
                );

        assertThat(SessionOperationType.values())
                .contains(
                        SessionOperationType.OPENED,
                        SessionOperationType.PAUSED,
                        SessionOperationType.RESUMED,
                        SessionOperationType.CLOSED,
                        SessionOperationType.CANCELLED,
                        SessionOperationType.CAPACITY_CHANGED
                );
    }
}
