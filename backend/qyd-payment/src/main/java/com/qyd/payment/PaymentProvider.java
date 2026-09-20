package com.qyd.payment;

import java.math.BigDecimal;

public interface PaymentProvider {
    String name();
    String initiate(String paymentNo, BigDecimal amount, String currency);
    boolean verify(String signature, String callbackId, String paymentNo, String status);
    String refund(String refundNo, String providerTransactionId, BigDecimal amount);
}
