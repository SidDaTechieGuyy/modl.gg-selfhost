package gg.modl.backend.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import gg.modl.backend.database.mongo.repository.ServerLookupRepository;
import gg.modl.backend.server.data.Server;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ServerServiceTest {
    @Test
    void resolvesOnlyTheInstanceDatabaseAndRequiresAnApiKeyForPlugins() {
        var repository = mock(ServerLookupRepository.class);
        var instance = new Server("Test", "modl_instance", "owner@example.com");
        when(repository.findByDatabaseName("modl_instance")).thenReturn(Optional.of(instance));
        var service = new ServerService(repository);
        assertSame(instance, service.getInstance());
        assertSame(instance, service.getServerByDatabaseName("modl_instance"));
        assertNull(service.getServerByDatabaseName("another_database"));
        assertNull(service.getServerByApiKey(""));
        verify(repository, never()).findByApiKey("");
    }
}
