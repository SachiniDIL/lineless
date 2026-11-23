package com.lineless.queue_service;

import com.lineless.queue_service.entity.Business;
import com.lineless.queue_service.entity.OutboxEvent;
import com.lineless.queue_service.entity.Queue;
import com.lineless.queue_service.entity.QueueEntry;
import com.lineless.queue_service.entity.QueueOperation;
import com.lineless.queue_service.entity.QueueSession;
import com.lineless.queue_service.enums.QueueEntryStatus;
import com.lineless.queue_service.enums.QueueOperationType;
import com.lineless.queue_service.enums.QueueSessionStatus;
import com.lineless.queue_service.repository.BusinessRepository;
import com.lineless.queue_service.repository.OutboxEventRepository;
import com.lineless.queue_service.repository.QueueEntryRepository;
import com.lineless.queue_service.repository.QueueOperationRepository;
import com.lineless.queue_service.repository.QueueRepository;
import com.lineless.queue_service.repository.QueueSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.node.JsonNodeFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class QueueServiceApplicationTests {

	@Autowired
	private BusinessRepository businessRepository;

	@Autowired
	private QueueRepository queueRepository;

	@Autowired
	private QueueSessionRepository queueSessionRepository;

	@Autowired
	private QueueEntryRepository queueEntryRepository;

	@Autowired
	private QueueOperationRepository queueOperationRepository;

	@Autowired
	private OutboxEventRepository outboxEventRepository;

	@Container
	static PostgreSQLContainer<?> postgres =
			new PostgreSQLContainer<>("postgres:17-alpine")
					.withDatabaseName("lineless_queue_test")
					.withUsername("lineless_test")
					.withPassword("lineless_test");

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
		registry.add("spring.jpa.properties.hibernate.type.json_format_mapper", () -> "jackson3");
		registry.add("spring.flyway.enabled", () -> "true");
		registry.add("spring.flyway.locations", () -> "classpath:db/migration");
	}

	@BeforeEach
	void cleanDatabase() {
		queueOperationRepository.deleteAll();
		queueEntryRepository.deleteAll();
		queueSessionRepository.deleteAll();
		queueRepository.deleteAll();
		businessRepository.deleteAll();
		outboxEventRepository.deleteAll();
	}

	@Test
	void contextLoads() {
	}

	@Test
	void shouldFindBusinessesByOwnerAndPublicStatus() {
		UUID ownerId = UUID.randomUUID();

		Business ownedPublic = createBusiness(ownerId, "Public Business", true);
		Business ownedPrivate = createBusiness(ownerId, "Private Business", false);
		createBusiness(UUID.randomUUID(), "Other Public Business", true);

		List<Business> ownedBusinesses = businessRepository.findByOwnerUserId(ownerId);
		List<Business> publicBusinesses = businessRepository.findByIsPublicTrue();

		assertThat(ownedBusinesses)
				.extracting(Business::getId)
				.containsExactlyInAnyOrder(ownedPublic.getId(), ownedPrivate.getId());
		assertThat(publicBusinesses)
				.extracting(Business::getId)
				.contains(ownedPublic.getId());
		assertThat(publicBusinesses)
				.extracting(Business::getId)
				.doesNotContain(ownedPrivate.getId());
	}

	@Test
	void shouldFindQueuesAndSessionsUsingParentIds() {
		Business business = createBusiness(UUID.randomUUID(), "Queue Business", true);
		Queue activeQueue = createQueue(business, "Main Queue", true);
		Queue inactiveQueue = createQueue(business, "Closed Queue", false);

		QueueSession firstSession = createSession(activeQueue, LocalDate.of(2026, 11, 20));
		QueueSession secondSession = createSession(activeQueue, LocalDate.of(2026, 11, 21));

		List<Queue> businessQueues = queueRepository.findByBusinessId(business.getId());
		List<Queue> activeQueues = queueRepository.findByBusinessIdAndIsActiveTrue(business.getId());
		QueueSession foundSession = queueSessionRepository
				.findByQueueIdAndSessionDate(activeQueue.getId(), secondSession.getSessionDate())
				.orElseThrow();
		List<QueueSession> orderedSessions =
				queueSessionRepository.findByQueueIdOrderBySessionDateAsc(activeQueue.getId());

		assertThat(businessQueues)
				.extracting(Queue::getId)
				.containsExactlyInAnyOrder(activeQueue.getId(), inactiveQueue.getId());
		assertThat(activeQueues)
				.extracting(Queue::getId)
				.containsExactly(activeQueue.getId());
		assertThat(foundSession.getId()).isEqualTo(secondSession.getId());
		assertThat(orderedSessions)
				.extracting(QueueSession::getId)
				.containsExactly(firstSession.getId(), secondSession.getId());
	}

	@Test
	void shouldFindActiveCustomerEntryCountAndNextWaitingEntry() {
		Business business = createBusiness(UUID.randomUUID(), "Entry Business", true);
		Queue queue = createQueue(business, "Entry Queue", true);
		QueueSession session = createSession(queue, LocalDate.of(2026, 11, 22));
		UUID customerId = UUID.randomUUID();

		QueueEntry waitingLater = createEntry(session, customerId, 2, "A002", QueueEntryStatus.WAITING);
		QueueEntry waitingNext = createEntry(session, UUID.randomUUID(), 1, "A001", QueueEntryStatus.WAITING);
		QueueEntry called = createEntry(session, UUID.randomUUID(), 3, "A003", QueueEntryStatus.CALLED);
		createEntry(session, UUID.randomUUID(), 4, "A004", QueueEntryStatus.COMPLETED);

		List<QueueEntry> orderedEntries =
				queueEntryRepository.findBySessionIdOrderByTicketNumberAsc(session.getId());
		QueueEntry activeCustomerEntry = queueEntryRepository
				.findFirstBySessionIdAndCustomerUserIdAndStatusInOrderByTicketNumberAsc(
						session.getId(),
						customerId,
						List.of(QueueEntryStatus.WAITING, QueueEntryStatus.CALLED)
				)
				.orElseThrow();
		long activeCount = queueEntryRepository.countBySessionIdAndStatusIn(
				session.getId(),
				List.of(QueueEntryStatus.WAITING, QueueEntryStatus.CALLED)
		);
		QueueEntry nextWaiting = queueEntryRepository
				.findFirstBySessionIdAndStatusOrderByTicketNumberAsc(
						session.getId(),
						QueueEntryStatus.WAITING
				)
				.orElseThrow();

		assertThat(orderedEntries)
				.extracting(QueueEntry::getTicketNumber)
				.containsExactly(1, 2, 3, 4);
		assertThat(activeCustomerEntry.getId()).isEqualTo(waitingLater.getId());
		assertThat(activeCount).isEqualTo(3);
		assertThat(nextWaiting.getId()).isEqualTo(waitingNext.getId());
		assertThat(called.getStatus()).isEqualTo(QueueEntryStatus.CALLED);
	}

	@Test
	void shouldRetrieveOperationHistoryInCreationOrder() {
		Business business = createBusiness(UUID.randomUUID(), "Operation Business", true);
		Queue queue = createQueue(business, "Operation Queue", true);
		QueueSession session = createSession(queue, LocalDate.of(2026, 11, 23));
		QueueEntry entry = createEntry(
				session,
				UUID.randomUUID(),
				1,
				"OP001",
				QueueEntryStatus.WAITING
		);

		QueueOperation joined = createOperation(
				entry,
				QueueOperationType.JOINED,
				UUID.randomUUID()
		);
		QueueOperation called = createOperation(
				entry,
				QueueOperationType.CALLED,
				UUID.randomUUID()
		);

		List<QueueOperation> operations =
				queueOperationRepository.findByQueueEntryIdOrderByCreatedAtAsc(entry.getId());

		assertThat(operations)
				.extracting(QueueOperation::getId)
				.containsExactly(joined.getId(), called.getId());
	}

	@Test
	void shouldRetrieveUnpublishedOutboxEventsInCreationOrder() {
		OutboxEvent first = createOutboxEvent("QUEUE", "QUEUE_JOINED");
		OutboxEvent second = createOutboxEvent("ENTRY", "TICKET_CALLED");

		OutboxEvent published = createOutboxEvent("QUEUE", "QUEUE_CLOSED");
		published.setPublishedAt(java.time.Instant.now());
		outboxEventRepository.save(published);

		List<OutboxEvent> unpublished =
				outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc();

		assertThat(unpublished)
				.extracting(OutboxEvent::getId)
				.containsExactly(first.getId(), second.getId());
	}

	private Business createBusiness(UUID ownerUserId, String name, boolean isPublic) {
		Business business = BeanUtils.instantiateClass(Business.class);
		business.setOwnerUserId(ownerUserId);
		business.setName(name);
		business.setPublic(isPublic);
		return businessRepository.saveAndFlush(business);
	}

	private Queue createQueue(Business business, String name, boolean isActive) {
		Queue queue = BeanUtils.instantiateClass(Queue.class);
		queue.setBusiness(business);
		queue.setName(name);
		queue.setMaxCapacity(50);
		queue.setAverageServiceTimeMinutes(10);
		queue.setActive(isActive);
		return queueRepository.saveAndFlush(queue);
	}

	private QueueSession createSession(Queue queue, LocalDate sessionDate) {
		QueueSession session = BeanUtils.instantiateClass(QueueSession.class);
		session.setQueue(queue);
		session.setSessionDate(sessionDate);
		session.setStatus(QueueSessionStatus.OPEN);
		session.setNextTicketNumber(1);
		return queueSessionRepository.saveAndFlush(session);
	}

	private QueueEntry createEntry(
			QueueSession session,
			UUID customerUserId,
			int ticketNumber,
			String ticketCode,
			QueueEntryStatus status
	) {
		QueueEntry entry = BeanUtils.instantiateClass(QueueEntry.class);
		entry.setSession(session);
		entry.setCustomerUserId(customerUserId);
		entry.setTicketNumber(ticketNumber);
		entry.setTicketCode(ticketCode);
		entry.setStatus(status);
		return queueEntryRepository.saveAndFlush(entry);
	}

	private QueueOperation createOperation(
			QueueEntry entry,
			QueueOperationType operationType,
			UUID performedByUserId
	) {
		QueueOperation operation = BeanUtils.instantiateClass(QueueOperation.class);
		operation.setQueueEntry(entry);
		operation.setOperationType(operationType);
		operation.setPerformedByUserId(performedByUserId);
		operation.setDetails(
				JsonNodeFactory.instance.objectNode()
						.put("operation", operationType.name())
		);
		return queueOperationRepository.saveAndFlush(operation);
	}

	private OutboxEvent createOutboxEvent(String aggregateType, String eventType) {
		OutboxEvent event = BeanUtils.instantiateClass(OutboxEvent.class);
		event.setAggregateType(aggregateType);
		event.setAggregateId(UUID.randomUUID());
		event.setEventType(eventType);
		event.setPayload(
				JsonNodeFactory.instance.objectNode()
						.put("eventType", eventType)
		);
		return outboxEventRepository.saveAndFlush(event);
	}

}
