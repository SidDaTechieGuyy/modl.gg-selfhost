package gg.modl.backend.storage.service;

import com.mongodb.client.result.UpdateResult;
import gg.modl.backend.database.mongo.fields.StorageFileDocumentFields;
import gg.modl.backend.database.mongo.repository.StorageFileMongoRepository;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.storage.config.StorageExecutorConfig;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageSyncService {
    private final LocalStorageService localStorageService;
    private final StorageFileMongoRepository storageFileRepository;
    private final StorageUsageAccountant storageUsageAccountant;

    private final Set<String> syncsInProgress = ConcurrentHashMap.newKeySet();

    @Async(StorageExecutorConfig.STORAGE_TASK_EXECUTOR)
    public void triggerAsyncSync(Server server) {
        String serverId = server.getId();
        if (!syncsInProgress.add(serverId)) {
            return;
        }

        try {
            syncServerFiles(server, false);
        } catch (Exception e) {
            log.warn("Async sync failed for server {}", server.getDatabaseName(), e);
        } finally {
            syncsInProgress.remove(serverId);
        }
    }

    public int syncServerFiles(Server server, boolean authoritative) {
        if (!localStorageService.isConfigured()) {
            log.warn("Skipping storage sync for server {}: Local storage is unavailable", server.getDatabaseName());
            return 0;
        }

        List<LocalStorageService.StoredObjectInfo> objects = localStorageService.listAllObjects(server);

        int inserted = 0;
        long totalSize = 0;
        for (LocalStorageService.StoredObjectInfo obj : objects) {
            String fileName = StorageKeyUtils.extractFileName(obj.key());
            String category = StorageKeyUtils.categorizeFile(obj.key());

            Query query = Query.query(Criteria.where(StorageFileDocumentFields.KEY).is(obj.key()));
            Update update = new Update()
                .setOnInsert(StorageFileDocumentFields.KEY, obj.key())
                .setOnInsert(StorageFileDocumentFields.FILE_NAME, fileName)
                .set(StorageFileDocumentFields.SIZE, obj.size())
                .setOnInsert(StorageFileDocumentFields.CONTENT_TYPE, "application/octet-stream")
                .setOnInsert(StorageFileDocumentFields.CATEGORY, category)
                .setOnInsert(StorageFileDocumentFields.CREATED_AT, Date.from(obj.lastModified()));

            UpdateResult result = storageFileRepository.upsert(server, query, update);
            if (result.getUpsertedId() != null) {
                inserted++;
            }
            totalSize += obj.size();
        }

        List<String> storedKeys = new ArrayList<>(objects.size());
        for (LocalStorageService.StoredObjectInfo obj : objects) {
            storedKeys.add(obj.key());
        }
        storageFileRepository.deleteByKeyNotIn(server, storedKeys);

        if (authoritative) {
            storageUsageAccountant.setAuthoritativeUsage(server, totalSize);
        } else {
            storageUsageAccountant.lowerUsageToActual(server, totalSize);
        }

        log.info("Synced {} files ({} new) for server {}", objects.size(), inserted, server.getDatabaseName());
        return objects.size();
    }
}
