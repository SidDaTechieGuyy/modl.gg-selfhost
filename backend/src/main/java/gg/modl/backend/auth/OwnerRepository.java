package gg.modl.backend.auth;

import gg.modl.backend.database.mongo.TenantMongoAccess;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OwnerRepository {
    private final TenantMongoAccess databases;

    public Optional<OwnerCredentials> findOwner() {
        return Optional.ofNullable(databases.global().findById("owner", OwnerCredentials.class));
    }

    public void insert(OwnerCredentials owner) {
        // MongoDB's built-in unique _id index arbitrates concurrent first requests.
        databases.global().insert(owner);
    }
}
