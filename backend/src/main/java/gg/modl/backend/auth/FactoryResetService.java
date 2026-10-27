package gg.modl.backend.auth;

import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.infrastructure.exception.ForbiddenException;
import gg.modl.backend.infrastructure.exception.UnauthorizedException;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.infrastructure.exception.ServiceUnavailableException;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/** A durable request is executed on the next process start, before accepting traffic. */
@Service
@Slf4j
public class FactoryResetService {
    private final OwnerRepository owners;
    private final TenantMongoAccess databases;
    private final Path root;
    private final Path marker;
    private volatile boolean requested;

    public FactoryResetService(OwnerRepository owners, TenantMongoAccess databases,
        @Value("${modl.storage.directory:/app/uploads}") String directory) {
        this.owners = owners;
        this.databases = databases;
        this.root = Path.of(directory).toAbsolutePath().normalize();
        this.marker = root.resolve(".factory-reset-requested");
    }

    public boolean isOwner(String email) {
        return email != null && owners.findOwner().map(owner -> owner.getEmail().equalsIgnoreCase(email)).orElse(false);
    }

    public boolean requested() { return requested; }

    public synchronized void request(String email, String password, String confirmation) {
        if (!isOwner(email)) throw new ForbiddenException("Only the instance owner can reset this installation.");
        if (!"RESET EVERYTHING".equals(confirmation)) throw new ValidationException("Type RESET EVERYTHING exactly to confirm.");
        var owner = owners.findOwner().orElseThrow(() -> new ForbiddenException("The instance has no owner."));
        if (password == null || password.getBytes(StandardCharsets.UTF_8).length > 72
            || !new BCryptPasswordEncoder().matches(password, owner.getPasswordHash()))
            throw new UnauthorizedException("The owner password is incorrect.");
        try {
            Files.createDirectories(root);
            Files.writeString(marker, "Factory reset authorized by instance owner.\n", StandardOpenOption.CREATE_NEW);
            requested = true;
        } catch (IOException e) {
            throw new ServiceUnavailableException("Unable to schedule the reset. No data has been deleted.", e);
        }
    }

    @PostConstruct
    public void completePendingReset() {
        if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) return;
        requested = true;
        try {
            if (Files.isSymbolicLink(root) || !Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Invalid reset marker or upload directory");
            // Never follow symlinks or delete the mounted directory itself. Keep the marker until ALL steps succeed.
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!file.equals(marker) && !file.equals(root.resolve(".instance-secret"))) Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                    if (error != null) throw error;
                    if (!directory.equals(root)) Files.delete(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
            clearDatabase(databases.forDatabase(PasswordAuthService.INSTANCE_DATABASE), false);
            clearDatabase(databases.forDatabase("training_data"), false);
            clearDatabase(databases.global(), true);
            // Owner is removed last so incomplete cleanup never reopens registration.
            databases.global().getCollection("instance_owner").deleteMany(new Document());
            Files.delete(marker);
            requested = false;
            log.warn("Factory reset completed. First-boot registration is open.");
        } catch (Exception e) {
            // Fail startup; the persistent marker makes a subsequent start retry safely.
            throw new IllegalStateException("Factory reset incomplete; correct database/upload access and restart to retry", e);
        }
    }

    private void clearDatabase(org.springframework.data.mongodb.core.MongoTemplate database, boolean preserveOwner) {
        for (String collection : database.getCollectionNames()) {
            if (!collection.startsWith("system.") && !(preserveOwner && collection.equals("instance_owner")))
                database.getCollection(collection).deleteMany(new Document());
        }
    }
}
