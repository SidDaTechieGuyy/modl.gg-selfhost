package gg.modl.backend.limits;

import static org.junit.jupiter.api.Assertions.*;
import gg.modl.backend.server.data.Server;
import org.junit.jupiter.api.Test;

class DefaultServerLimitPolicyTest {
    @Test
    void allInstanceFeaturesAreAvailableWithOperationalUploadLimits() {
        var limits = new DefaultServerLimitPolicy().resolve(new Server("Test", "modl_instance", "owner@example.com"));
        assertTrue(limits.isAiModerationEnabled());
        assertEquals(Integer.MAX_VALUE, limits.getMaxStaffSeats());
        assertEquals(Long.MAX_VALUE, limits.getMaxStorageBytes());
        assertEquals(Long.MAX_VALUE, limits.getAiRequestLimit());
        assertFalse(limits.exceedsUploadLimit(1024L * 1024 * 1024));
        assertTrue(limits.exceedsUploadLimit(1024L * 1024 * 1024 + 1));
    }
}
