package com.qyd.marketing;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class MarketingDomainTest {
    @Test void couponRequestMapsToStrictEntityContract(){
        var request=new MarketingController.CouponRequest("新客券","首次运动",new BigDecimal("10.00"),
                new BigDecimal("50.00"),100,Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"),CouponStatus.ACTIVE);
        CouponDefinition entity=new CouponDefinition(request);
        assertEquals("新客券",entity.name);assertEquals(0,entity.claimedQuantity);
        assertEquals(CouponStatus.ACTIVE,entity.status);assertEquals(new BigDecimal("10.00"),entity.discountAmount);
    }
}
