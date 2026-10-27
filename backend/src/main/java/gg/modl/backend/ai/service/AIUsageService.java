package gg.modl.backend.ai.service;

import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.server.data.Server;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIUsageService {
    private final TenantMongoAccess databases;
    private volatile boolean recordingHealthy = true;
    static final String COLLECTION = "ai_usage";

    public void recordSuccessfulRequest(Server server, String service) {
        if (!java.util.Set.of("moderation", "ticket_analysis", "appeal_analysis", "other").contains(service))
            throw new IllegalArgumentException("Unknown AI usage service");
        try {
            databases.forServer(server).upsert(Query.query(Criteria.where("_id").is(currentPeriod())),
                new Update().inc("byService." + service, 1L), COLLECTION);
            recordingHealthy = true;
        } catch (RuntimeException e) {
            recordingHealthy = false;
            log.error("Unable to record AI request usage; moderation result is preserved", e);
        }
    }

    public record Usage(String period, long totalUsed, Map<String, Long> byService, boolean trackingAvailable) {}

    public Usage getUsage(Server server) {
        String period = currentPeriod();
        Document record = databases.forServer(server).findById(period, Document.class, COLLECTION);
        Document counts = record == null ? new Document() : record.get("byService", Document.class);
        Map<String, Long> services = new LinkedHashMap<>();
        for (String name : new String[]{"moderation", "ticket_analysis", "appeal_analysis", "other"}) {
            Object value = counts == null ? null : counts.get(name);
            services.put(name, value instanceof Number number ? number.longValue() : 0L);
        }
        return new Usage(period, services.values().stream().mapToLong(Long::longValue).sum(), services, recordingHealthy);
    }

    private String currentPeriod() { return YearMonth.now(ZoneOffset.UTC).toString(); }
}
