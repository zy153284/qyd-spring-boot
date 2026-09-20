package com.qyd.infrastructure.event;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared inbox claim used by event listeners before side effects.
 * A false result means this consumer already handled (or concurrently claimed) the event.
 */
@Service
public class IdempotentConsumer {
    private final ConsumedEventRepository repository;
    public IdempotentConsumer(ConsumedEventRepository repository){this.repository=repository;}

    @Transactional
    public boolean claim(String consumer,String eventId) {
        if(repository.findByConsumerAndEventId(consumer,eventId).isPresent()) return false;
        ConsumedEvent item=new ConsumedEvent();item.consumer=consumer;item.eventId=eventId;
        try { repository.saveAndFlush(item); return true; }
        catch (DataIntegrityViolationException duplicate) { return false; }
    }
}
