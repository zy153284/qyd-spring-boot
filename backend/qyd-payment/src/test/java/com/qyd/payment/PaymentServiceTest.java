package com.qyd.payment;

import com.qyd.order.OrderApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentServiceTest {
    @Test
    void callbackRejectsAmountMismatchBeforeRecordingOrAdvancingOrder() {
        PaymentRepository payments=mock(PaymentRepository.class);
        CallbackRepository callbacks=mock(CallbackRepository.class);
        OrderApplicationService orders=mock(OrderApplicationService.class);
        PaymentProvider provider=mock(PaymentProvider.class);
        when(provider.name()).thenReturn("mock");
        when(provider.verify(anyString(),anyString(),anyString(),anyString())).thenReturn(true);
        when(callbacks.findByProviderAndCallbackId("mock","cb-new")).thenReturn(Optional.empty());
        when(payments.findByPaymentNo("P1")).thenReturn(Optional.of(payment()));
        PaymentService service=new PaymentService(payments,mock(RefundRepository.class),callbacks,orders,provider);

        assertThrows(ResponseStatusException.class,()->service.callback("sig",
                new PaymentController.CallbackRequest("cb-new","P1","txn",new BigDecimal("99.99"),"SUCCESS")));
        verify(callbacks,never()).save(any());
        verifyNoInteractions(orders);
    }

    @Test
    void callbackReplayDoesNotAdvanceOrderTwice() {
        PaymentRepository payments=mock(PaymentRepository.class);
        CallbackRepository callbacks=mock(CallbackRepository.class);
        OrderApplicationService orders=mock(OrderApplicationService.class);
        PaymentProvider provider=mock(PaymentProvider.class);
        Payment payment=payment();
        when(provider.name()).thenReturn("mock");when(provider.verify(anyString(),anyString(),anyString(),anyString())).thenReturn(true);
        PaymentCallback prior=new PaymentCallback();prior.paymentId=payment.getId();
        when(callbacks.findByProviderAndCallbackId("mock","cb1")).thenReturn(Optional.of(prior));
        when(payments.findById(payment.getId())).thenReturn(Optional.of(payment));
        PaymentService service=new PaymentService(payments,mock(RefundRepository.class),callbacks,orders,provider);
        var result=service.callback("sig",new PaymentController.CallbackRequest("cb1","P1","txn",new BigDecimal("100.00"),"SUCCESS"));
        assertTrue(result.duplicate());
        verifyNoInteractions(orders);
    }

    @Test
    void refundCannotExceedSuccessfulPayment() {
        PaymentRepository payments=mock(PaymentRepository.class);RefundRepository refunds=mock(RefundRepository.class);
        Payment payment=payment();payment.status=PaymentStatus.SUCCEEDED;
        when(payments.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(refunds.sumActiveByPaymentId(payment.getId())).thenReturn(new BigDecimal("90.00"));
        OrderApplicationService orders=mock(OrderApplicationService.class);
        when(orders.snapshot("order")).thenReturn(new OrderApplicationService.OrderSnapshot("order","O1","user",new BigDecimal("100.00"),"CNY","PAID"));
        PaymentService service=new PaymentService(payments,refunds,mock(CallbackRepository.class),orders,mock(PaymentProvider.class));
        assertThrows(ResponseStatusException.class,()->service.refund(payment.getId(),new BigDecimal("10.01"),"test","user"));
        verify(refunds,never()).save(any());
    }

    private static Payment payment(){
        Payment p=new Payment();p.orderId="order";p.paymentNo="P1";p.amount=new BigDecimal("100.00");
        p.currency="CNY";p.status=PaymentStatus.PENDING;return p;
    }
}
