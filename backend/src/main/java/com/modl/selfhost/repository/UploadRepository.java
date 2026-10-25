package com.modl.selfhost.repository;

import com.modl.selfhost.model.UploadRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UploadRepository extends MongoRepository<UploadRecord, String> {
}
