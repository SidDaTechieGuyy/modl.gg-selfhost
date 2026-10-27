package gg.modl.backend.auth;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Persistent installation signing key. Never log or expose its value. */
@Component
public final class InstanceSecret {
    private final String value;
    public String getValue() { return value; }

    public InstanceSecret(@Value("${modl.storage.directory:/app/uploads}") String directory,
                          @Value("${SESSION_SECRET:}") String configured) {
        try {
            Path root = Files.createDirectories(Path.of(directory).toAbsolutePath().normalize()).toRealPath();
            Path file = root.resolve(".instance-secret");
            if (configured == null) configured = "";
            if (!configured.isEmpty() && configured.length() < 32)
                throw new IllegalArgumentException("An optional SESSION_SECRET override must have at least 32 characters");
            if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                byte[] random = new byte[48];
                new SecureRandom().nextBytes(random);
                String candidate = configured.isEmpty() ? Base64.getUrlEncoder().withoutPadding().encodeToString(random) : configured;
                Path temporary = Files.createTempFile(root, ".secret-", ".tmp");
                try {
                    restrict(temporary);
                    Files.writeString(temporary, candidate);
                    // Atomic no-replace publication: concurrent initializers must use the winning key.
                    try { Files.createLink(file, temporary); }
                    catch (FileAlreadyExistsException ignored) { }
                } finally { Files.deleteIfExists(temporary); }
            }
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 4096)
                throw new IOException("Invalid persistent signing key file");
            restrict(file);
            value = Files.readString(file);
            if (value.length() < 32) throw new IOException("Persistent signing key is invalid");
            if (!configured.isEmpty() && !java.security.MessageDigest.isEqual(value.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                configured.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                throw new IOException("SESSION_SECRET differs from the persisted signing key; restore the original value or remove the override");
        } catch (IOException e) {
            throw new IllegalStateException("Cannot initialize persistent signing key; check uploads volume permissions", e);
        }
    }

    private static void restrict(Path file) throws IOException {
        if (Files.getFileStore(file).supportsFileAttributeView("posix"))
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
    }
}
