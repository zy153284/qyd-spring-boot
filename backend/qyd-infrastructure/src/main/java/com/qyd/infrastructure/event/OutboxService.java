package com.qyd.infrastructure.event;

import com.qyd.shared.domain.BaseEntity;
import jakarta.persistence.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Transactional event foundation. Call enqueue in the same transaction as the
 * aggregate change. The poller publishes an envelope; listeners must use
 * IdempotentConsumer before performing side effects.
 */
@Service
public class OutboxService {
    private final OutboxRepository repository;
    private final ApplicationEventPublisher publisher;

    public OutboxService(OutboxRepository repository, ApplicationEventPublisher publisher) {
        this.repository=repository;
        this.publisher=publisher;
    }

    @Transactional
    public String enqueue(String aggregateType,String aggregateId,String eventType,String payload) {
        OutboxEvent event=new OutboxEvent();
        event.aggregateType=aggregateType;event.aggregateId=aggregateId;
        event.eventType=eventType;event.payload=payload;event.occurredAt=Instant.now();
        return repository.save(event).getId();
    }

    @Scheduled(fixedDelayString="${qyd.outbox.publish-interval:PT1S}")
    @Transactional
    public void publishBatch() {
        for (OutboxEvent event:repository.lockNextBatch(PageRequest.of(0,100))) {
            publisher.publishEvent(new OutboxEnvelope(event.getId(),event.aggregateType,event.aggregateId,
                    event.eventType,event.payload,event.occurredAt));
            event.publishedAt=Instant.now();
            event.attempts++;
        }
    }

    public record OutboxEnvelope(String eventId,String aggregateType,String aggregateId,
                                 String eventType,String payload,Instant occurredAt) {}
}

@Entity @Table(name="outbox_event")
class OutboxEvent extends BaseEntity {
    @Column(nullable=false,length=80)String aggregateType;
    @Column(nullable=false,length=36)String aggregateId;
    @Column(nullable=false,length=120)String eventType;
    @Column(nullable=false,columnDefinition="TEXT",length=65535)String payload;
    @Column(nullable=false)Instant occurredAt;
    Instant publishedAt;
    @Column(nullable=false)int attempts;
}

@Entity
@Table(name="consumed_event",uniqueConstraints=@UniqueConstraint(name="uk_consumed_event",columnNames={"consumer","event_id"}))
class ConsumedEvent extends BaseEntity {
    @Column(nullable=false,length=100)String consumer;
    @Column(nullable=false,length=36)String eventId;
}

interface OutboxRepository extends JpaRepository<OutboxEvent,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from OutboxEvent e where e.publishedAt is null order by e.occurredAt")
    List<OutboxEvent> lockNextBatch(org.springframework.data.domain.Pageable pageable);
}
interface ConsumedEventRepository extends JpaRepository<ConsumedEvent,String> {
    Optional<ConsumedEvent> findByConsumerAndEventId(String consumer,String eventId);
}
