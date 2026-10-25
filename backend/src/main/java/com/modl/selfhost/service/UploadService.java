package com.modl.selfhost.service;

import com.modl.selfhost.config.AppProperties;
import com.modl.selfhost.model.UploadRecord;
import com.modl.selfhost.repository.UploadRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class UploadService {
    private final AppProperties appProperties;
    private final UploadRepository uploadRepository;

    public UploadService(AppProperties appProperties, UploadRepository uploadRepository) {
        this.appProperties = appProperties;
        this.uploadRepository = uploadRepository;
    }

    public UploadRecord save(MultipartFile file, String ownerId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A file is required.");
        }
        Path uploadDir = Path.of(appProperties.getUploadDir()).toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);

        String original = file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename();
        String safeName = UUID.randomUUID() + "-" + original.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path target = uploadDir.resolve(safeName);
        file.transferTo(target);

        String url = "/uploads/" + safeName;
        UploadRecord record = new UploadRecord(safeName, original, file.getSize(), url, ownerId);
        return uploadRepository.save(record);
    }
}
