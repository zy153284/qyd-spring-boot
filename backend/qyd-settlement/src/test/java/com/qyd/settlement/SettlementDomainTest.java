package com.qyd.settlement;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class SettlementDomainTest {
    @Test void generatedSettlementBalancesGrossAdjustmentsAndNet(){
        EligibilityRepository eligibility=mock(EligibilityRepository.class);
        StatementRepository statements=mock(StatementRepository.class);
        AdjustmentRepository adjustments=mock(AdjustmentRepository.class);
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        SettlementEligibility first=item("60.00"),second=item("40.00");
        SettlementAdjustment refund=new SettlementAdjustment();refund.amount=new BigDecimal("-15.00");
        when(jdbc.queryForList(any(String.class))).thenReturn(List.of());
        when(eligibility.findEligible(any(),any(),any())).thenReturn(List.of(first,second));
        when(adjustments.findPendingForVenue("venue")).thenReturn(List.of(refund));
        when(statements.save(any())).thenAnswer(invocation->invocation.getArgument(0));

        var dto=new SettlementService(eligibility,statements,adjustments,jdbc).generate("key",
                new SettlementController.GenerateRequest("venue",Instant.EPOCH,Instant.EPOCH.plusSeconds(60)));

        assertEquals(new BigDecimal("100.00"),dto.grossAmount());
        assertEquals(new BigDecimal("-15.00"),dto.adjustmentAmount());
        assertEquals(new BigDecimal("85.00"),dto.netAmount());
        assertEquals(0,dto.grossAmount().add(dto.adjustmentAmount()).compareTo(dto.netAmount()));
    }

    private SettlementEligibility item(String amount){
        SettlementEligibility item=new SettlementEligibility();item.venueId="venue";
        item.currency="CNY";item.eligibleAmount=new BigDecimal(amount);return item;
    }

    @Test void statusFlowIsCompleteAndOrdered(){
        assertArrayEquals(new SettlementStatus[]{SettlementStatus.DRAFT,SettlementStatus.CONFIRMED,
                SettlementStatus.FROZEN,SettlementStatus.PAYING,SettlementStatus.PAID,SettlementStatus.COMPLETED},
                SettlementStatus.values());
    }
}
