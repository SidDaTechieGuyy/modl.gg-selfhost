package gg.modl.backend.ai.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.server.data.Server;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

class AIUsageServiceTest {
    TenantMongoAccess databases = mock(TenantMongoAccess.class);
    MongoTemplate mongo = mock(MongoTemplate.class);
    Server server = new Server("Test", "modl_instance", "owner");
    AIUsageService service() { when(databases.forServer(server)).thenReturn(mongo); return new AIUsageService(databases); }

    @Test void incrementsAtomicallyAndReadsPersistedBreakdown() {
        var usage = service();
        usage.recordSuccessfulRequest(server, "moderation");
        var update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).upsert(any(Query.class), update.capture(), eq("ai_usage"));
        assertEquals(1L, ((Document) update.getValue().getUpdateObject().get("$inc")).get("byService.moderation"));
        when(mongo.findById(anyString(), eq(Document.class), eq("ai_usage")))
            .thenReturn(new Document("byService", new Document("moderation", 3L).append("ticket_analysis", 2)));
        var result = usage.getUsage(server);
        assertEquals(5, result.totalUsed());
        assertEquals(3, result.byService().get("moderation"));
        assertEquals(java.time.YearMonth.now(java.time.ZoneOffset.UTC).toString(), result.period());
        assertEquals(5, service().getUsage(server).totalUsed());
    }
    @Test void freshCountersStartAtZeroAndCannotUseArbitraryFieldPaths() {
        var usage = service();
        assertEquals(0, usage.getUsage(server).totalUsed());
        assertThrows(IllegalArgumentException.class, () -> usage.recordSuccessfulRequest(server, "x.$set"));
    }
    @Test void countingFailureDoesNotLoseTheModerationResultAndIsReported() {
        var usage = service();
        doThrow(new IllegalStateException("offline")).when(mongo).upsert(any(Query.class), any(Update.class), eq("ai_usage"));
        assertDoesNotThrow(() -> usage.recordSuccessfulRequest(server, "moderation"));
        assertFalse(usage.getUsage(server).trackingAvailable());
    }
}
