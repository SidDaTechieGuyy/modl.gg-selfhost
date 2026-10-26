package gg.modl.backend.server;

import gg.modl.backend.database.mongo.repository.ServerLookupRepository;
import gg.modl.backend.server.data.Server;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ServerService {
    private final ServerLookupRepository repository;
    public Server getInstance() { return repository.findByDatabaseName("modl_instance").orElse(null); }
    public Server getServerByDatabaseName(String name) { return "modl_instance".equals(name) ? getInstance() : null; }
    public Server getServerByApiKey(String key) { return key == null || key.isBlank() ? null : repository.findByApiKey(key).orElse(null); }
    public void evictAllServerCaches() {}
    public void evictServerCache(String ignored) {}
    public void evictApiKey(String ignored) {}
}
