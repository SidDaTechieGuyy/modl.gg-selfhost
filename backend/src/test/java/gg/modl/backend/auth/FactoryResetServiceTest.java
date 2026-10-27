package gg.modl.backend.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mongodb.client.MongoCollection;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.infrastructure.exception.*;
import java.nio.file.*;
import java.util.Optional;
import java.util.Set;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class FactoryResetServiceTest {
    @TempDir Path root;
    OwnerRepository owners = mock(OwnerRepository.class);
    TenantMongoAccess databases = mock(TenantMongoAccess.class);
    MongoTemplate global = mock(MongoTemplate.class);
    MongoTemplate instance = mock(MongoTemplate.class);
    MongoTemplate training = mock(MongoTemplate.class);
    MongoCollection<Document> ownerCollection = mock(MongoCollection.class);
    MongoCollection<Document> records = mock(MongoCollection.class);
    FactoryResetService service;

    @BeforeEach void setup() {
        when(owners.findOwner()).thenReturn(Optional.of(new OwnerCredentials("owner", "owner", "owner@example.test",
            new BCryptPasswordEncoder(4).encode("owner-password"), "Test")));
        when(databases.global()).thenReturn(global);
        when(databases.forDatabase("modl_instance")).thenReturn(instance);
        when(databases.forDatabase("training_data")).thenReturn(training);
        when(global.getCollectionNames()).thenReturn(Set.of("instance_owner", "servers", "system.profile"));
        when(instance.getCollectionNames()).thenReturn(Set.of("sessions", "tickets"));
        when(training.getCollectionNames()).thenReturn(Set.of("training"));
        when(global.getCollection("instance_owner")).thenReturn(ownerCollection);
        when(global.getCollection("servers")).thenReturn(records);
        when(instance.getCollection(anyString())).thenReturn(records);
        when(training.getCollection(anyString())).thenReturn(records);
        service = new FactoryResetService(owners, databases, root.toString());
    }

    @Test void rejectsNonOwnerWrongPasswordAndMissingConfirmationWithoutDeletingAnything() {
        assertThrows(ForbiddenException.class, () -> service.request("staff@example.test", "owner-password", "RESET EVERYTHING"));
        assertThrows(ForbiddenException.class, () -> service.request(null, "owner-password", "RESET EVERYTHING"));
        assertThrows(ValidationException.class, () -> service.request("owner@example.test", "owner-password", "reset everything"));
        assertThrows(UnauthorizedException.class, () -> service.request("owner@example.test", "incorrect", "RESET EVERYTHING"));
        assertFalse(service.requested());
        assertFalse(Files.exists(root.resolve(".factory-reset-requested")));
        verifyNoInteractions(databases);
    }

    @Test void requestIsDurableAndOnlyDeletesOnFreshStartup() throws Exception {
        Path file = Files.createDirectories(root.resolve("nested")).resolve("replay.bin");
        Files.writeString(file, "recording");
        Files.createDirectories(root.resolve(".metadata"));
        Files.writeString(root.resolve(".metadata/file.properties"), "metadata");
        service.request("owner@example.test", "owner-password", "RESET EVERYTHING");
        assertTrue(service.requested());
        assertTrue(Files.exists(file));
        verifyNoInteractions(databases);
        var fresh = new FactoryResetService(owners, databases, root.toString());
        fresh.completePendingReset();
        assertFalse(fresh.requested());
        try (var files = Files.list(root)) { assertEquals(0, files.count()); }
        var ordered = inOrder(records, ownerCollection);
        ordered.verify(records, times(4)).deleteMany(new Document());
        ordered.verify(ownerCollection).deleteMany(new Document());
        verify(global, never()).getCollection("system.profile");
        verify(databases, never()).forDatabase("admin");
    }

    @Test void incompleteResetKeepsMarkerAndOwnerAndRetriesOnNextStart() throws Exception {
        service.request("owner@example.test", "owner-password", "RESET EVERYTHING");
        when(instance.getCollectionNames()).thenThrow(new IllegalStateException("database offline"));
        assertThrows(IllegalStateException.class, service::completePendingReset);
        assertTrue(Files.exists(root.resolve(".factory-reset-requested")));
        verifyNoInteractions(ownerCollection);
        doReturn(Set.of("tickets")).when(instance).getCollectionNames();
        service.completePendingReset();
        assertFalse(service.requested());
        assertFalse(Files.exists(root.resolve(".factory-reset-requested")));
    }

    @Test void ordinaryStartupDoesNotTouchData() {
        service.completePendingReset();
        verifyNoInteractions(databases);
    }

    @Test void resetPreservesThePersistentSigningKey() throws Exception {
        Files.writeString(root.resolve(".instance-secret"), "persistent-key-not-application-data");
        service.request("owner@example.test", "owner-password", "RESET EVERYTHING");
        service.completePendingReset();
        assertEquals("persistent-key-not-application-data", Files.readString(root.resolve(".instance-secret")));
    }

    @Test void refusesDuplicateRequest() {
        service.request("owner@example.test", "owner-password", "RESET EVERYTHING");
        assertThrows(ServiceUnavailableException.class, () -> service.request("owner@example.test", "owner-password", "RESET EVERYTHING"));
    }

    @Test void deletesSymlinkWithoutTouchingItsTarget(@TempDir Path outside) throws Exception {
        Files.writeString(outside.resolve("keep.txt"), "keep");
        Files.createSymbolicLink(root.resolve("link"), outside);
        service.request("owner@example.test", "owner-password", "RESET EVERYTHING");
        service.completePendingReset();
        assertEquals("keep", Files.readString(outside.resolve("keep.txt")));
    }
}
