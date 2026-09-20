package com.qyd.order;

import com.qyd.product.InventoryService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderApplicationServiceTest {
    @Test
    void createReturnsExistingOrderForSameIdempotencyKey() {
        OrderRepository orders=mock(OrderRepository.class);
        CustomerOrder existing=order(OrderStatus.PENDING_PAYMENT);
        when(orders.findByUserIdAndIdempotencyKey("u","key")).thenReturn(Optional.of(existing));
        InventoryService inventory=mock(InventoryService.class);
        OrderApplicationService service=new OrderApplicationService(orders,mock(OrderItemRepository.class),inventory,mock(RedemptionRepository.class));
        var result=service.create("u","key",new OrderApplicationService.CreateOrder(java.util.List.of()));
        assertEquals(existing.getId(),result.id());
        verifyNoInteractions(inventory);
    }

    @Test
    void successfulPaymentAdvancesOrderAndConfirmsInventory() {
        OrderRepository orders=mock(OrderRepository.class);
        CustomerOrder order=order(OrderStatus.PENDING_PAYMENT);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        InventoryService inventory=mock(InventoryService.class);
        OrderApplicationService service=new OrderApplicationService(orders,mock(OrderItemRepository.class),inventory,mock(RedemptionRepository.class));
        assertFalse(service.markPaid(order.getId()).isBlank());
        assertEquals("PAID",service.snapshot(order.getId()).status());
        verify(inventory).confirmOrder(order.getId());
    }

    private static CustomerOrder order(OrderStatus status){
        CustomerOrder o=new CustomerOrder();o.orderNo="O1";o.userId="u";o.idempotencyKey="key";
        o.amount=new BigDecimal("100.00");o.currency="CNY";o.status=status;return o;
    }
}
