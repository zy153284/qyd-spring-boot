package com.qyd.bootstrap;

import com.qyd.infrastructure.event.OutboxService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class PersistenceAndInventoryIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired OutboxService outbox;

    @Test
    void jpaOutboxPersistsAgainstFlywayManagedH2Schema() {
        String id=outbox.enqueue("Order","order-1","OrderCreated","{\"orderId\":\"order-1\"}");
        assertEquals(1,jdbc.queryForObject("select count(*) from outbox_event where id=?",Integer.class,id));
    }

    @Test
    void concurrentConditionalUpdatesNeverOversell() throws Exception {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        jdbc.update("delete from slot_inventory where id='concurrent-slot'");
        jdbc.update("""
            insert into slot_inventory(id,sku_id,starts_at,ends_at,capacity,reserved,sold,created_at,updated_at,version)
            values('concurrent-slot','sku',?,?,?,0,0,?,?,0)
            """,Instant.now(),Instant.now().plusSeconds(3600),10,Instant.now(),Instant.now());
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");

        ExecutorService pool=Executors.newFixedThreadPool(20);
        CountDownLatch start=new CountDownLatch(1);
        ArrayList<Future<Integer>> results=new ArrayList<>();
        for(int i=0;i<20;i++) results.add(pool.submit(()->{
            start.await();
            return new TransactionTemplate(transactions).execute(status->jdbc.update(
                    "update slot_inventory set reserved=reserved+1,version=version+1 where id='concurrent-slot' and capacity-reserved-sold>=1"));
        }));
        start.countDown();
        int accepted=0;
        for(Future<Integer> result:results) accepted+=result.get(10,TimeUnit.SECONDS);
        pool.shutdownNow();

        assertEquals(10,accepted);
        assertEquals(10,jdbc.queryForObject("select reserved from slot_inventory where id='concurrent-slot'",Integer.class));
    }
}
