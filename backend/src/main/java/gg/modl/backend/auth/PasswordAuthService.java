package gg.modl.backend.auth;

import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.infrastructure.exception.ConflictException;
import gg.modl.backend.infrastructure.exception.ServiceUnavailableException;
import gg.modl.backend.infrastructure.exception.UnauthorizedException;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.ProvisioningStatus;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.service.ServerProvisioningService;
import gg.modl.backend.settings.service.ApiKeySettingsService;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordAuthService {
    public static final String INSTANCE_ID = "000000000000000000000001";
    public static final String INSTANCE_DATABASE = "modl_instance";
    private final OwnerRepository owners;
    private final TenantMongoAccess databases;
    private final ServerProvisioningService provisioning;
    private final ApiKeySettingsService apiKeys;
    private final ServerService servers;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    private final String dummyHash = encoder.encode("invalid-account-timing-padding");

    public boolean registrationOpen() { return owners.findOwner().isEmpty(); }

    public Server register(String username, String email, String password, String serverName) {
        if (!registrationOpen()) throw new ConflictException("Registration is closed. This instance already has an owner.");
        validatePassword(password);
        String normalizedUsername = normalizeUsername(username);
        String normalizedEmail = normalize(email);
        OwnerCredentials owner = new OwnerCredentials("owner", normalizedUsername, normalizedEmail,
            encoder.encode(password), serverName.trim());
        try {
            owners.insert(owner);
        } catch (DuplicateKeyException e) {
            throw new ConflictException("Registration is closed. This instance already has an owner.");
        }
        return ensureProvisioned(owner);
    }

    public record LoginResult(Server server, String email) {}

    public LoginResult login(String identifier, String password) {
        OwnerCredentials owner = owners.findOwner().orElse(null);
        String normalized = normalize(identifier);
        boolean identityMatches = owner != null && owner.getUsername().equalsIgnoreCase(normalized);
        gg.modl.backend.staff.data.Staff staff = null;
        Server server = instance();
        if (!identityMatches && server != null && !normalized.isEmpty()) {
            var query = new org.springframework.data.mongodb.core.query.Query(
                org.springframework.data.mongodb.core.query.Criteria.where("username").regex("^" + java.util.regex.Pattern.quote(normalized) + "$", "i"));
            staff = databases.forServer(server).findOne(query, gg.modl.backend.staff.data.Staff.class, gg.modl.backend.database.CollectionName.STAFF);
        }
        String storedHash = identityMatches ? owner.getPasswordHash() : staff != null ? staff.getPasswordHash() : null;
        boolean passwordMatches = password != null && password.getBytes(StandardCharsets.UTF_8).length <= 72
            && encoder.matches(password, storedHash == null ? dummyHash : storedHash);
        if (storedHash == null || !passwordMatches) throw new UnauthorizedException("Invalid username or password.");
        return identityMatches ? new LoginResult(ensureProvisioned(owner), owner.getEmail()) : new LoginResult(server, staff.getEmail());
    }

    public Server instance() {
        return databases.global().findById(INSTANCE_ID, Server.class);
    }

    public String ownerUsername() {
        return owners.findOwner().map(OwnerCredentials::getUsername).orElse("owner");
    }

    public String validateAvailableUsername(Server server, String accountId, String username) {
        String normalized = normalizeUsername(username);
        var owner = owners.findOwner().orElse(null);
        if (owner != null && !owner.getEmail().equalsIgnoreCase(accountId)
            && owner.getUsername().equalsIgnoreCase(normalized)) throw new ConflictException("Username already in use.");
        var criteria = org.springframework.data.mongodb.core.query.Criteria.where("username")
            .regex("^" + java.util.regex.Pattern.quote(normalized) + "$", "i");
        if (accountId != null) criteria = new org.springframework.data.mongodb.core.query.Criteria().andOperator(criteria,
            org.springframework.data.mongodb.core.query.Criteria.where("email").ne(accountId));
        if (databases.forServer(server).exists(new org.springframework.data.mongodb.core.query.Query(criteria),
            gg.modl.backend.staff.data.Staff.class, gg.modl.backend.database.CollectionName.STAFF))
            throw new ConflictException("Username already in use.");
        return normalized;
    }

    public void updateOwnerUsername(String accountId, String username) {
        var owner = owners.findOwner().orElse(null);
        if (owner != null && owner.getEmail().equalsIgnoreCase(accountId))
            databases.global().updateFirst(org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("_id").is("owner")),
                new org.springframework.data.mongodb.core.query.Update().set("username", normalizeUsername(username)), OwnerCredentials.class);
    }

    public static String normalizeUsername(String username) {
        String normalized = normalize(username);
        if (!normalized.matches("[a-z0-9_-]{3,32}")) throw new ValidationException("Username must use 3–32 letters, numbers, underscores or hyphens.");
        return normalized;
    }

    private synchronized Server ensureProvisioned(OwnerCredentials owner) {
        Server server = instance();
        if (server == null) {
            server = new Server(owner.getServerName(), INSTANCE_DATABASE, owner.getEmail());
            server.setId(INSTANCE_ID);
            server.setCreatedAt(new Date());
            server.setProvisioningStatus(ProvisioningStatus.PENDING);
            try { databases.global().insert(server); }
            catch (DuplicateKeyException e) { server = instance(); }
        }
        if (server.getProvisioningStatus() == ProvisioningStatus.COMPLETED) return server;
        try {
            provisioning.provision(server);
            if (server.getApiKey() == null || server.getApiKey().isBlank()) {
                String key = apiKeys.generateApiKey(server, "default");
                apiKeys.syncApiKeyToServer(server, key);
                server.setApiKey(key);
            }
            server.setProvisioningStatus(ProvisioningStatus.COMPLETED);
            server.setUpdatedAt(new Date());
            databases.global().save(server);
            servers.evictAllServerCaches();
            return server;
        } catch (Exception e) {
            log.error("Instance initialization failed; the owner can log in again to retry", e);
            throw new ServiceUnavailableException("Your owner account was saved, but instance setup failed. Log in to retry setup.");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ValidationException("Password must contain at least 8 characters and at most 72 UTF-8 bytes.");
    }
}
