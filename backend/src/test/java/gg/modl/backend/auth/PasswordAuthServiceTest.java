package gg.modl.backend.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.infrastructure.exception.*;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.*;
import gg.modl.backend.server.service.ServerProvisioningService;
import gg.modl.backend.settings.service.ApiKeySettingsService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PasswordAuthServiceTest {
    private final OwnerRepository owners = mock(OwnerRepository.class);
    private final TenantMongoAccess databases = mock(TenantMongoAccess.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final ServerProvisioningService provisioning = mock(ServerProvisioningService.class);
    private final ApiKeySettingsService keys = mock(ApiKeySettingsService.class);
    private final ServerService servers = mock(ServerService.class);

    private PasswordAuthService service() {
        when(databases.global()).thenReturn(mongo);
        when(databases.forServer(any())).thenReturn(mongo);
        Server server = new Server("Test", "modl_instance", "owner@example.com");
        server.setProvisioningStatus(ProvisioningStatus.COMPLETED);
        when(mongo.findById(PasswordAuthService.INSTANCE_ID, Server.class)).thenReturn(server);
        return new PasswordAuthService(owners, databases, provisioning, keys, servers);
    }

    @Test
    void storesASaltedHashAndNormalizesIdentifiers() {
        when(owners.findOwner()).thenReturn(Optional.empty());
        PasswordAuthService service = service();
        service.register("Owner", "Owner@example.com", "a-long-password!", "Test Server");
        ArgumentCaptor<OwnerCredentials> saved = ArgumentCaptor.forClass(OwnerCredentials.class);
        verify(owners).insert(saved.capture());
        assertNotEquals("a-long-password!", saved.getValue().getPasswordHash());
        assertTrue(new BCryptPasswordEncoder().matches("a-long-password!", saved.getValue().getPasswordHash()));
        assertEquals("owner", saved.getValue().getUsername());
        assertEquals("owner@example.com", saved.getValue().getEmail());
        assertEquals("owner", saved.getValue().getId());
    }

    @Test
    void closesRegistrationWhenAnOwnerExists() {
        when(owners.findOwner()).thenReturn(Optional.of(owner()));
        PasswordAuthService service = service();
        assertFalse(service.registrationOpen());
        assertThrows(ConflictException.class, () -> service.register("other", "other@example.com", "another-password!", "Another Server"));
        verify(owners, never()).insert(any());
    }

    @Test
    void databaseUniquenessClosesConcurrentRegistration() {
        when(owners.findOwner()).thenReturn(Optional.empty());
        doThrow(new DuplicateKeyException("owner")).when(owners).insert(any());
        assertThrows(ConflictException.class, () -> service().register("other", "other@example.com", "another-password!", "Another Server"));
        verifyNoInteractions(provisioning);
    }

    @Test
    void acceptsUsernameOrEmailAndRejectsWrongCredentials() {
        when(owners.findOwner()).thenReturn(Optional.of(owner()));
        PasswordAuthService service = service();
        assertNotNull(service.login("OWNER", "a-long-password!"));
        assertNotNull(service.login("owner@example.com", "a-long-password!"));
        assertThrows(UnauthorizedException.class, () -> service.login("owner", "wrong-password"));
        assertThrows(UnauthorizedException.class, () -> service.login("stranger", "a-long-password!"));
    }

    @Test
    void enforcesBcryptByteLimitIncludingUnicode() {
        assertThrows(ValidationException.class, () -> PasswordAuthService.validatePassword("short"));
        assertThrows(ValidationException.class, () -> PasswordAuthService.validatePassword("界".repeat(25)));
        assertDoesNotThrow(() -> PasswordAuthService.validatePassword("界".repeat(24)));
    }

    private OwnerCredentials owner() {
        return new OwnerCredentials("owner", "owner", "owner@example.com",
            new BCryptPasswordEncoder(4).encode("a-long-password!"), "Test");
    }
}
