package gg.modl.backend.storage.service;

import gg.modl.backend.infrastructure.exception.ForbiddenException;
import gg.modl.backend.infrastructure.exception.ResourceNotFoundException;
import gg.modl.backend.infrastructure.exception.ServiceUnavailableException;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.storage.dto.response.PresignUploadResponse;
import gg.modl.backend.storage.dto.response.StorageFileResponse;
import gg.modl.backend.storage.dto.response.UploadResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Persistent files with signed upload/download capabilities, preserving the existing two-step upload API. */
@Service
public class LocalStorageService {
    private final Path root;
    private final byte[] secret;
    private final String baseUrl;
    private static final long MAX_UPLOAD = 1024L * 1024 * 1024;
    public record UploadFileResult(String key, String cdnUrl) {}
    public record StoredObjectInfo(String key, long size, Instant lastModified) {}
    public record Transfer(String operation, String key, String contentType, long size, long expiresAt) {}

    public LocalStorageService(@Value("${modl.storage.directory:/app/uploads}") String directory,
                               @Value("#{@instanceSecret.value}") String secret,
                               @Value("${BASE_URL:http://localhost:8080}") String baseUrl) {
        if (secret == null || secret.length() < 32) throw new IllegalArgumentException("SESSION_SECRET must have at least 32 characters");
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        try { root = Files.createDirectories(Path.of(directory).toAbsolutePath().normalize()).toRealPath(); }
        catch (IOException e) { throw new IllegalStateException("Cannot initialize upload directory", e); }
    }

    public boolean isConfigured() { return true; }
    public String getCdnDomain() { return java.net.URI.create(baseUrl).getAuthority(); }

    public Path path(String key) {
        if (key == null || key.length() > 1024 || !key.matches("[A-Za-z0-9._/-]+")
            || key.startsWith("/") || Arrays.stream(key.split("/", -1)).anyMatch(s -> s.isEmpty() || s.equals(".") || s.equals("..")))
            throw new ValidationException("Invalid file key");
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) throw new ValidationException("Invalid file key");
        for (Path current = path; current != null && !current.equals(root); current = current.getParent())
            if (Files.isSymbolicLink(current)) throw new ForbiddenException("Symbolic links are not allowed in upload paths");
        return path;
    }

    private Path metadata(String key) { return root.resolve(".metadata").resolve(key + ".properties"); }

    public Transfer verify(String token, String operation) {
        try {
            if (token == null || token.length() > 4096) throw new IllegalArgumentException();
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2 || !MessageDigest.isEqual(sign(parts[0]), Base64.getUrlDecoder().decode(parts[1])))
                throw new IllegalArgumentException();
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(Base64.getUrlDecoder().decode(parts[0])))) {
                Transfer transfer = new Transfer(input.readUTF(), input.readUTF(), input.readUTF(), input.readLong(), input.readLong());
                if (!operation.equals(transfer.operation()) || input.available() != 0
                    || (transfer.expiresAt() > 0 && transfer.expiresAt() <= Instant.now().getEpochSecond())) throw new IllegalArgumentException();
                path(transfer.key());
                return transfer;
            }
        } catch (IOException | IllegalArgumentException e) { throw new ForbiddenException("Invalid or expired file link"); }
    }

    private String url(String operation, String key, String type, long size, long expiry) {
        path(key);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream data = new DataOutputStream(bytes)) {
                data.writeUTF(operation); data.writeUTF(key); data.writeUTF(type); data.writeLong(size); data.writeLong(expiry);
            }
            String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
            return baseUrl + "/api/v1/public/files/" + operation + "?token=" + payload + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload));
        } catch (IOException e) { throw new IllegalStateException("Cannot create file link", e); }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII));
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException("Cannot sign file link", e); }
    }

    public PresignUploadResponse createPresignedUploadUrl(Server server, String type, String name, String contentType, long size) {
        return createPresignedUploadUrl(server, type, name, contentType, size, null);
    }
    public PresignUploadResponse createPresignedUploadUrl(Server server, String type, String name, String contentType, long size, String entityId) {
        return uploadLink(StorageKeyUtils.buildKey(server, type, name, entityId), contentType, size);
    }
    public PresignUploadResponse uploadLink(String key, String contentType, long size) {
        if (size <= 0 || size > MAX_UPLOAD) throw new ValidationException("Upload size must be between 1 byte and 1 GiB");
        Instant expires = Instant.now().plus(Duration.ofMinutes(15));
        return new PresignUploadResponse(url("upload", key, contentType, size, expires.getEpochSecond()), key,
            expires, "PUT", Map.of("Content-Type", contentType));
    }

    public void store(Transfer transfer, InputStream input, String contentType) {
        if (!"upload".equals(transfer.operation()) || transfer.size() <= 0 || transfer.size() > MAX_UPLOAD
            || !transfer.contentType().equals(contentType)) throw new ValidationException("Upload does not match its declared metadata");
        Path destination = path(transfer.key());
        Path temp = null;
        Path metaTemp = null;
        boolean published = false;
        try {
            Files.createDirectories(destination.getParent());
            Files.createDirectories(metadata(transfer.key()).getParent());
            temp = Files.createTempFile(destination.getParent(), ".upload-", ".tmp");
            try (OutputStream output = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[64 * 1024];
                long remaining = transfer.size();
                while (remaining > 0) {
                    int count = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (count == -1) throw new ValidationException("Upload is shorter than the declared size");
                    output.write(buffer, 0, count); remaining -= count;
                }
                if (input.read() != -1) throw new ValidationException("Upload exceeds the declared size");
            }
            Properties properties = new Properties();
            properties.setProperty("contentType", transfer.contentType());
            metaTemp = Files.createTempFile(metadata(transfer.key()).getParent(), ".meta-", ".tmp");
            try (OutputStream output = Files.newOutputStream(metaTemp)) { properties.store(output, null); }
            // A hard link publishes a complete file atomically and fails if the key already exists.
            Files.createLink(destination, temp);
            published = true;
            Files.move(metaTemp, metadata(transfer.key()), StandardCopyOption.REPLACE_EXISTING);
        } catch (FileAlreadyExistsException e) {
            throw new gg.modl.backend.infrastructure.exception.ConflictException("This upload has already been stored");
        } catch (IOException e) {
            if (published) try { Files.deleteIfExists(destination); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
            throw new ServiceUnavailableException("Unable to store file. Check upload volume permissions and free space.", e);
        } finally {
            try { if (temp != null) Files.deleteIfExists(temp); if (metaTemp != null) Files.deleteIfExists(metaTemp); }
            catch (IOException e) { /* Incomplete files are hidden and never returned by listing. */ }
        }
    }

    public byte[] downloadBytes(String key) {
        try { return Files.readAllBytes(path(key)); }
        catch (NoSuchFileException e) { throw new ResourceNotFoundException("File not found"); }
        catch (IOException e) { throw new ServiceUnavailableException("Unable to read file", e); }
    }
    public String getCdnUrl(String key) { return url("download", key, "", 0, 0); }
    public String getPresignedUrl(String key) { return getCdnUrl(key); }
    public String extractKeyFromDownloadUrl(String url) {
        try {
            var address = java.net.URI.create(url);
            var base = java.net.URI.create(baseUrl);
            if (!base.getScheme().equalsIgnoreCase(address.getScheme())
                || !base.getRawAuthority().equalsIgnoreCase(address.getRawAuthority())
                || !"/api/v1/public/files/download".equals(address.getPath()) || address.getFragment() != null) return null;
            String query = address.getRawQuery();
            if (query == null || !query.startsWith("token=") || query.contains("&")) return null;
            return verify(query.substring(6), "download").key();
        } catch (IllegalArgumentException | ForbiddenException e) { return null; }
    }
    public boolean verifyUploadExists(String key) { return Files.isRegularFile(path(key)) && Files.isRegularFile(metadata(key)); }
    public UploadResponse getUploadDetails(String key) {
        if (!verifyUploadExists(key)) return null;
        try {
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(metadata(key))) { properties.load(input); }
            return new UploadResponse(key, getCdnUrl(key), StorageKeyUtils.extractFileName(key), Files.size(path(key)),
                properties.getProperty("contentType", "application/octet-stream"));
        } catch (IOException e) { throw new ServiceUnavailableException("Unable to read file metadata", e); }
    }
    public boolean deleteFile(String key) {
        try { Files.deleteIfExists(path(key)); Files.deleteIfExists(metadata(key)); return true; }
        catch (IOException e) { throw new ServiceUnavailableException("Unable to delete file", e); }
    }
    public int bulkDelete(List<String> keys) { int count = 0; for (String key : keys) if (deleteFile(key)) count++; return count; }
    public List<StoredObjectInfo> listAllObjects(Server server) { return listObjectInfosByPrefix(server.getDatabaseName() + "/"); }
    public List<StoredObjectInfo> listObjectInfosByPrefix(String prefix) {
        String safePrefix = prefix == null ? "" : prefix;
        if (!safePrefix.isEmpty()) path(safePrefix.replaceAll("/+$", ""));
        try (var paths = Files.walk(root)) {
            List<StoredObjectInfo> result = new ArrayList<>();
            for (Path item : paths.filter(Files::isRegularFile).toList()) {
                String key = root.relativize(item).toString().replace('\\', '/');
                if (key.startsWith(safePrefix) && !key.startsWith(".metadata/") && !item.getFileName().toString().startsWith(".")
                    && verifyUploadExists(key)) result.add(new StoredObjectInfo(key, Files.size(item), Files.getLastModifiedTime(item).toInstant()));
            }
            return result;
        } catch (IOException e) { throw new ServiceUnavailableException("Unable to list stored files", e); }
    }
    public List<StorageFileResponse> listFiles(Server server, String prefix) {
        return listObjectInfosByPrefix(server.getDatabaseName() + "/" + (prefix == null ? "" : prefix)).stream()
            .map(info -> new StorageFileResponse(info.key(), StorageKeyUtils.extractFileName(info.key()), info.size(),
                getUploadDetails(info.key()).contentType(), Date.from(info.lastModified()), getCdnUrl(info.key()))).toList();
    }
    public Map<String, Long> calculateStorageByType(Server server) {
        Map<String, Long> totals = new HashMap<>();
        listAllObjects(server).forEach(info -> totals.merge(StorageKeyUtils.categorizeFile(info.key()), info.size(), Long::sum));
        return totals;
    }
    public UploadFileResult uploadFile(Server server, String type, String name, String contentType, byte[] data) {
        String key = StorageKeyUtils.buildKey(server, type, name, null);
        store(new Transfer("upload", key, contentType, data.length, 0), new ByteArrayInputStream(data), contentType);
        return new UploadFileResult(key, getCdnUrl(key));
    }
}
