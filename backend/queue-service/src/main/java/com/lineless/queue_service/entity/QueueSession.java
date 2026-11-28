package com.lineless.queue_service.entity;

import com.lineless.queue_service.enums.QueueSessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "queue_sessions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_queue_sessions_queue_date",
                        columnNames = {"queue_id", "session_date"}
                )
        }
)
@Getter
@Setter
public class QueueSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "queue_id", nullable = false)
    private Queue queue;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private QueueSessionStatus status;

    @Column(name = "next_ticket_number", nullable = false)
    private int nextTicketNumber = 1;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "scheduled_start_at", nullable = false)
    private Instant scheduledStartAt;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected QueueSession() {
    }
}
