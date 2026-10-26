package gg.modl.backend.settings.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.server.data.Server;
import gg.modl.backend.storage.service.LocalStorageService;
import gg.modl.backend.storage.service.StorageMetadataService;
import gg.modl.backend.storage.service.StorageQuotaService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

class IconUploadServiceTest {
    @Test
    void uploadDeletesObjectWhenAtomicQuotaReservationFails() throws Exception {
        LocalStorageService localStorageService = mock(LocalStorageService.class);
        StorageQuotaService storageQuotaService = mock(StorageQuotaService.class);
        IconUploadService service = new IconUploadService(
            localStorageService,
            storageQuotaService,
            mock(StorageMetadataService.class)
        );
        Server server = new Server("server", "db", "admin@example.com");
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("image/png");
        when(file.getSize()).thenReturn(1024L);
        when(file.getOriginalFilename()).thenReturn("icon.png");
        when(file.getBytes()).thenReturn(new byte[10]);
        when(localStorageService.isConfigured()).thenReturn(true);
        when(storageQuotaService.canUpload(server, 1024L)).thenReturn(true);
        when(localStorageService.uploadFile(eq(server), eq("icons/homepage"), eq("icon.png"), eq("image/png"), any(byte[].class)))
            .thenReturn(new LocalStorageService.UploadFileResult("db/icons/homepage/icon.png", "https://cdn/icon.png"));
        when(storageQuotaService.confirmAndRecordFile(server, "db/icons/homepage/icon.png", 1024L, "image/png"))
            .thenReturn(StorageQuotaService.ConfirmResult.QUOTA_EXCEEDED);

        ResponseEntity<?> response = service.uploadIcon(server, file, "homepage");

        assertEquals(400, response.getStatusCode().value());
        verify(localStorageService).deleteFile("db/icons/homepage/icon.png");
    }
}
