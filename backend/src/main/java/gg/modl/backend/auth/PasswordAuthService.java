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
        String normalizedUsername = normalize(username);
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
        boolean identityMatches = owner != null &&
            (owner.getUsername().equals(normalized) || owner.getEmail().equals(normalized));
        gg.modl.backend.staff.data.Staff staff = null;
        Server server = instance();
        if (!identityMatches && server != null && !normalized.isEmpty()) {
            var query = new org.springframework.data.mongodb.core.query.Query(new org.springframework.data.mongodb.core.query.Criteria().orOperator(
                org.springframework.data.mongodb.core.query.Criteria.where("email").regex("^" + java.util.regex.Pattern.quote(normalized) + "$", "i"),
                org.springframework.data.mongodb.core.query.Criteria.where("username").regex("^" + java.util.regex.Pattern.quote(normalized) + "$", "i")));
            staff = databases.forServer(server).findOne(query, gg.modl.backend.staff.data.Staff.class, gg.modl.backend.database.CollectionName.STAFF);
        }
        String storedHash = identityMatches ? owner.getPasswordHash() : staff != null ? staff.getPasswordHash() : null;
        boolean passwordMatches = password != null && password.getBytes(StandardCharsets.UTF_8).length <= 72
            && encoder.matches(password, storedHash == null ? dummyHash : storedHash);
        if (storedHash == null || !passwordMatches) throw new UnauthorizedException("Invalid username/email or password.");
        return identityMatches ? new LoginResult(ensureProvisioned(owner), owner.getEmail()) : new LoginResult(server, staff.getEmail());
    }

    public Server instance() {
        return databases.global().findById(INSTANCE_ID, Server.class);
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
        if (password == null || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ValidationException("Password must contain at least 12 characters and at most 72 UTF-8 bytes.");
    }
}
