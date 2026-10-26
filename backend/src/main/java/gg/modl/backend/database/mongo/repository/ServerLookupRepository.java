package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.ServerFields;
import gg.modl.backend.server.data.Server;
import java.util.Optional;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class ServerLookupRepository extends AbstractGlobalMongoRepository<Server> {
    public ServerLookupRepository(TenantMongoAccess access) { super(Server.class, CollectionName.MODL_SERVERS, access); }
    public Optional<Server> findByDatabaseName(String name) { return findOne(Query.query(Criteria.where(ServerFields.DATABASE_NAME).is(name))); }
    public Optional<Server> findByApiKey(String key) { return findOne(Query.query(Criteria.where(ServerFields.API_KEY).is(key))); }
}
