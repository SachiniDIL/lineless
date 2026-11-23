package com.lineless.queue_service.entity;

import com.lineless.queue_service.enums.QueueEntryStatus;
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
import java.util.UUID;

@Entity
@Table(
        name = "queue_entries",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_queue_entries_session_ticket_number",
                        columnNames = {"session_id", "ticket_number"}
                ),
                @UniqueConstraint(
                        name = "uq_queue_entries_session_ticket_code",
                        columnNames = {"session_id", "ticket_code"}
                )
        }
)
@Getter
@Setter
public class QueueEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private QueueSession session;

    @Column(name = "customer_user_id", nullable = false)
    private UUID customerUserId;

    @Column(name = "ticket_number", nullable = false)
    private int ticketNumber;

    @Column(name = "ticket_code", nullable = false, length = 30)
    private String ticketCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private QueueEntryStatus status;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "called_at")
    private Instant calledAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected QueueEntry() {
    }
}
