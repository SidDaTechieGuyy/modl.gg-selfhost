package com.modl.selfhost.api;

import com.modl.selfhost.config.AppProperties;
import com.modl.selfhost.model.UploadRecord;
import com.modl.selfhost.service.UploadService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class UploadController {
    private final UploadService uploadService;
    private final AppProperties appProperties;

    public UploadController(UploadService uploadService, AppProperties appProperties) {
        this.uploadService = uploadService;
        this.appProperties = appProperties;
    }

    @PostMapping("/uploads")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        try {
            String ownerId = request.getHeader("X-User-Id");
            if (ownerId == null || ownerId.isBlank()) {
                return ResponseEntity.status(401).body(Map.of("error", "Authentication required."));
            }
            UploadRecord record = uploadService.save(file, ownerId);
            return ResponseEntity.status(201).body(Map.of(
                    "id", record.getId(),
                    "filename", record.getFilename(),
                    "originalName", record.getOriginalName(),
                    "size", record.getSize(),
                    "url", record.getUrl()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }
}
