package gg.modl.backend.replaylite.storage;

import gg.modl.backend.storage.service.LocalStorageService;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReplayLiteStorageService {
    private final LocalStorageService storage;
    public boolean isConfigured() { return true; }
    public PresignedUpload createPresignedUpload(String objectKey, long contentLength) {
        var upload = storage.uploadLink(objectKey, "application/octet-stream", contentLength);
        return new PresignedUpload(upload.presignedUrl(), upload.method(), upload.requiredHeaders(), upload.expiresAt());
    }
    public Optional<ObjectMetadata> headObject(String objectKey) {
        var info = storage.getUploadDetails(objectKey);
        if (info == null) return Optional.empty();
        try { return Optional.of(new ObjectMetadata(info.size(), java.nio.file.Files.getLastModifiedTime(storage.path(objectKey)).toInstant())); }
        catch (java.io.IOException e) { throw new gg.modl.backend.infrastructure.exception.ServiceUnavailableException("Unable to read replay metadata", e); }
    }
    public PresignedDownload createPresignedDownload(String objectKey, String filename, Duration duration) {
        return new PresignedDownload(storage.getPresignedUrl(objectKey), Instant.now().plus(duration));
    }
    public boolean deleteObject(String objectKey) { return storage.deleteFile(objectKey); }
    public record PresignedUpload(String uploadUrl, String method, Map<String,String> requiredHeaders, Instant expiresAt) {}
    public record ObjectMetadata(long size, Instant lastModified) {}
    public record PresignedDownload(String url, Instant expiresAt) {}
}
