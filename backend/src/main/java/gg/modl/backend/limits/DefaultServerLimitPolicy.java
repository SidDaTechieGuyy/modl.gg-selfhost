package gg.modl.backend.limits;

import gg.modl.backend.server.data.Server;
import org.springframework.stereotype.Component;

@Component
public class DefaultServerLimitPolicy implements ServerLimitPolicy {
    @Override
    public ServerLimits resolve(Server server) {
        return ServerLimits.builder().maxStaffSeats(Integer.MAX_VALUE).maxStorageBytes(Long.MAX_VALUE)
            .aiModerationEnabled(true).aiRequestLimit(Long.MAX_VALUE).customDomainAllowed(false)
            .migrationFileSizeLimit(500L * 1024 * 1024).maxUploadBytes(1024L * 1024 * 1024).build();
    }
}
