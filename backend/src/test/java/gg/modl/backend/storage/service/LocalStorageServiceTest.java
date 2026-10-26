package gg.modl.backend.storage.service;

import static org.junit.jupiter.api.Assertions.*;
import gg.modl.backend.infrastructure.exception.*;
import gg.modl.backend.server.data.Server;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalStorageServiceTest {
    @TempDir Path directory;
    private LocalStorageService storage() {
        return new LocalStorageService(directory.toString(), "test-secret-with-at-least-32-characters", "http://localhost:8080");
    }
    private String token(String url) { return URI.create(url).getRawQuery().substring("token=".length()); }

    @Test
    void signedUploadPersistsAcrossServiceRestarts() {
        var storage = storage();
        var server = new Server("Test", "modl_instance", "owner@example.com");
        var upload = storage.createPresignedUploadUrl(server, "evidence", "proof.txt", "text/plain", 5);
        storage.store(storage.verify(token(upload.presignedUrl()), "upload"), new ByteArrayInputStream("proof".getBytes()), "text/plain");
        var restarted = storage();
        assertArrayEquals("proof".getBytes(), restarted.downloadBytes(upload.key()));
        assertEquals(5, restarted.getUploadDetails(upload.key()).size());
        assertEquals("text/plain", restarted.getUploadDetails(upload.key()).contentType());
        assertEquals(1, restarted.listAllObjects(server).size());
        String downloadUrl = restarted.getCdnUrl(upload.key());
        assertEquals(upload.key(), restarted.extractKeyFromDownloadUrl(downloadUrl));
        assertNull(restarted.extractKeyFromDownloadUrl(downloadUrl.replace("localhost:8080", "evil.example")));
        assertNull(restarted.extractKeyFromDownloadUrl(downloadUrl + "x"));
        assertTrue(restarted.deleteFile(upload.key()));
        assertNull(restarted.getUploadDetails(upload.key()));
    }

    @Test
    void rejectsTraversalAndSymbolicLinks() throws Exception {
        for (String key : new String[]{"../outside", "/etc/passwd", "a/../../b", "a\\..\\b", "a//b", "a/./b"})
            assertThrows(ValidationException.class, () -> storage().path(key));
        Path outside = Files.createTempDirectory("modl-storage-outside");
        try {
            Files.createSymbolicLink(directory.resolve("link"), outside);
            assertThrows(ForbiddenException.class, () -> storage().path("link/file"));
        } finally { Files.deleteIfExists(outside); }
    }

    @Test
    void refusesTamperedExpiredOrWrongOperationCapabilities() {
        var storage = storage();
        var upload = storage.uploadLink("modl_instance/ticket/file.txt", "text/plain", 5);
        String token = token(upload.presignedUrl());
        assertThrows(ForbiddenException.class, () -> storage.verify(token + "x", "upload"));
        assertThrows(ForbiddenException.class, () -> storage.verify(token, "download"));
        String expired = org.springframework.test.util.ReflectionTestUtils.invokeMethod(storage, "url", "upload",
            "modl_instance/ticket/expired.txt", "text/plain", 5L, Instant.now().minusSeconds(1).getEpochSecond());
        assertThrows(ForbiddenException.class, () -> storage.verify(token(expired), "upload"));
        var other = new LocalStorageService(directory.toString(), "different-secret-of-at-least-32-characters", "http://localhost:8080");
        assertThrows(ForbiddenException.class, () -> other.verify(token, "upload"));
    }

    @Test
    void refusesMismatchedLengthAndContentTypeWithoutPublishingFiles() {
        var storage = storage();
        var upload = storage.uploadLink("modl_instance/ticket/file.txt", "text/plain", 5);
        var transfer = storage.verify(token(upload.presignedUrl()), "upload");
        assertThrows(ValidationException.class, () -> storage.store(transfer, new ByteArrayInputStream(new byte[6]), "text/plain"));
        assertThrows(ValidationException.class, () -> storage.store(transfer, new ByteArrayInputStream(new byte[4]), "text/plain"));
        assertThrows(ValidationException.class, () -> storage.store(transfer, new ByteArrayInputStream(new byte[5]), "text/html"));
        assertFalse(storage.verifyUploadExists(upload.key()));
    }

    @Test
    void capabilityCannotOverwriteAnExistingFile() {
        var storage = storage();
        var upload = storage.uploadLink("modl_instance/ticket/file.txt", "text/plain", 5);
        var transfer = storage.verify(token(upload.presignedUrl()), "upload");
        storage.store(transfer, new ByteArrayInputStream("first".getBytes()), "text/plain");
        assertThrows(ConflictException.class, () -> storage.store(transfer, new ByteArrayInputStream("other".getBytes()), "text/plain"));
        assertArrayEquals("first".getBytes(), storage.downloadBytes(upload.key()));
    }
}
