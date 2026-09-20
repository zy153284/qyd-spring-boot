package com.qyd.bootstrap;

import com.qyd.shared.security.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class RoleContractTest {
    @Test
    void backendExposesOnlyCanonicalRoles() {
        assertArrayEquals(new Role[]{
                Role.CUSTOMER,Role.PLATFORM_ADMIN,Role.OPERATOR,
                Role.FINANCE,Role.VENUE_ADMIN,Role.VENUE_STAFF
        },Role.values());
    }
}
