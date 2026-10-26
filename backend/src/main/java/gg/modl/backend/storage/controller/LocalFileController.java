package gg.modl.backend.storage.controller;

import gg.modl.backend.infrastructure.exception.ResourceNotFoundException;
import gg.modl.backend.storage.service.LocalStorageService;
import gg.modl.backend.storage.dto.response.UploadResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/public/files")
@RequiredArgsConstructor
public class LocalFileController {
    private final LocalStorageService storage;

    @PutMapping("/upload")
    public ResponseEntity<Void> upload(@RequestParam String token, HttpServletRequest request) throws IOException {
        storage.store(storage.verify(token, "upload"), request.getInputStream(), request.getContentType());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/download")
    public ResponseEntity<FileSystemResource> download(@RequestParam String token) {
        var transfer = storage.verify(token, "download");
        UploadResponse details = storage.getUploadDetails(transfer.key());
        if (details == null) throw new ResourceNotFoundException("File not found");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(details.fileName()).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .header("Content-Security-Policy", "sandbox; default-src 'none'")
            .cacheControl(CacheControl.noStore())
            .contentType(MediaType.parseMediaType(details.contentType()))
            .body(new FileSystemResource(storage.path(transfer.key())));
    }
}
