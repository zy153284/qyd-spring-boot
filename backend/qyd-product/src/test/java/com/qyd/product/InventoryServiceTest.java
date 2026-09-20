package com.qyd.product;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryServiceTest {
    @Test
    void expiredReservationsAreReleasedExactlyOncePerLockedBatch() {
        SlotInventoryRepository inventory=mock(SlotInventoryRepository.class);
        InventoryReservationRepository reservations=mock(InventoryReservationRepository.class);
        InventoryReservation expired=new InventoryReservation();
        expired.slotId="slot";expired.quantity=2;expired.status=ReservationStatus.RESERVED;
        when(reservations.findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAt(eq(ReservationStatus.RESERVED),any()))
                .thenReturn(java.util.List.of(expired));
        when(inventory.release("slot",2)).thenReturn(1);

        new InventoryService(inventory,reservations,mock(SkuRepository.class)).releaseExpiredReservations();

        assertEquals(ReservationStatus.EXPIRED,expired.status);
        verify(inventory).release("slot",2);
    }

    @Test
    void conditionalReservePreventsOverselling() {
        SlotInventoryRepository inventory=mock(SlotInventoryRepository.class);
        InventoryReservationRepository reservations=mock(InventoryReservationRepository.class);
        InventoryService service=new InventoryService(inventory,reservations,mock(SkuRepository.class));
        when(inventory.reserve("slot",2)).thenReturn(0);
        assertThrows(ResponseStatusException.class,()->service.reserve("slot","order","item",2,Instant.now()));
        verify(reservations,never()).save(any());
    }

    @Test
    void repeatedReservationByOrderItemIsIdempotent() {
        SlotInventoryRepository inventory=mock(SlotInventoryRepository.class);
        InventoryReservationRepository reservations=mock(InventoryReservationRepository.class);
        InventoryReservation existing=new InventoryReservation();
        existing.slotId="slot";existing.orderId="order";existing.orderItemId="item";existing.quantity=1;
        existing.status=ReservationStatus.RESERVED;existing.expiresAt=Instant.now();
        when(reservations.findByOrderItemId("item")).thenReturn(java.util.Optional.of(existing));
        InventoryService service=new InventoryService(inventory,reservations,mock(SkuRepository.class));
        assertEquals(existing.getId(),service.reserve("slot","order","item",1,Instant.now()).id());
        verify(inventory,never()).reserve(anyString(),anyInt());
    }
}
