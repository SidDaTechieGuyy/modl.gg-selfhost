package gg.modl.backend.storage.service;

import gg.modl.backend.limits.ServerLimitPolicy;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.storage.dto.response.StorageQuotaResponse;
import gg.modl.backend.infrastructure.util.ByteFormatUtil;
import java.util.Map;
import java.util.OptionalLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageQuotaService {
    private final StorageMetadataService storageMetadataService;
    private final StorageUsageAccountant storageUsageAccountant;
    private final StorageSyncService storageSyncService;
    private final ServerLimitPolicy serverLimitPolicy;

    public enum ConfirmResult {
        SUCCESS,
        QUOTA_EXCEEDED,
        RECORD_FAILED
    }

    public boolean canUpload(Server server, long fileSize) {
        if (fileSize < 0) {
            return false;
        }
        long used = currentTrackedUsage(server);
        long max = getMaxBytesForServer(server);
        return fileSize <= max - used;
    }

    public boolean isWithinQuota(Server server) {
        long used = currentTrackedUsage(server);
        long max = getMaxBytesForServer(server);
        return used <= max;
    }

    private long currentTrackedUsage(Server server) {
        OptionalLong tracked = storageUsageAccountant.trackedBytes(server);
        if (tracked.isPresent()) {
            return tracked.getAsLong();
        }
        storageSyncService.triggerAsyncSync(server);
        return 0L;
    }

    public ConfirmResult confirmAndRecordFile(Server server, String key, long size, String contentType) {
        if (size < 0) {
            return ConfirmResult.RECORD_FAILED;
        }
        if (storageMetadataService.hasFile(server, key)) {
            return ConfirmResult.SUCCESS;
        }

        long maxBytes = getMaxBytesForServer(server);
        if (!storageUsageAccountant.tryReserveWithinLimit(server, size, maxBytes)) {
            return ConfirmResult.QUOTA_EXCEEDED;
        }

        StorageMetadataService.RecordFileResult recordResult =
            storageMetadataService.recordReservedFile(server, key, size, contentType);
        if (recordResult == StorageMetadataService.RecordFileResult.INSERTED) {
            return ConfirmResult.SUCCESS;
        }

        storageUsageAccountant.recordRemoval(server, size);
        return recordResult == StorageMetadataService.RecordFileResult.ALREADY_EXISTS
               ? ConfirmResult.SUCCESS
               : ConfirmResult.RECORD_FAILED;
    }

    public StorageQuotaResponse getQuota(Server server) {
        Map<String, Long> byType = storageMetadataService.calculateStorageByType(server);
        long usedBytes = byType.values()
            .stream().mapToLong(Long::longValue).sum();
        long maxBytes = getMaxBytesForServer(server);

        double usedPercentage = maxBytes > 0 ? (double) usedBytes / maxBytes * 100 : 0;

        return new StorageQuotaResponse(usedBytes, maxBytes, Math.round(usedPercentage * 100) / 100.0,
            ByteFormatUtil.format(usedBytes), "Available disk space", byType,
            new StorageQuotaResponse.AiQuotaInfo(0, Long.MAX_VALUE, 0, 0, true, 0, Map.of()));
    }

    private long getMaxBytesForServer(Server server) {
        return serverLimitPolicy.resolve(server).getMaxStorageBytes();
    }

}
