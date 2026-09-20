package com.qyd.risk;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RiskDomainTest {
    @Test void blacklistSubjectTypeIsNormalized(){
        RiskBlacklist item=new RiskBlacklist();
        item.update(new RiskController.BlacklistRequest("user_id","u-1","chargeback",true,null));
        assertEquals("USER_ID",item.subjectType);assertTrue(item.active);
    }
}
