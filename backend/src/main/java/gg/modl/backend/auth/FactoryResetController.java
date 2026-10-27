package gg.modl.backend.auth;

import gg.modl.backend.infrastructure.authorization.RequiresPanelPermission;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.role.service.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/panel/settings/factory-reset")
@RequiresPanelPermission(view = PermissionService.ADMIN_SETTINGS_VIEW, modify = "admin.settings.modify")
@RequiredArgsConstructor
public class FactoryResetController {
    private final FactoryResetService reset;
    private final FactoryResetRestart restart;

    public record ResetRequest(@NotBlank @Size(max = 72) String password,
                               @NotBlank @Size(max = 32) String confirmation) {}

    @GetMapping
    public Map<String, Boolean> status(HttpServletRequest request) {
        return Map.of("owner", reset.isOwner(RequestUtil.getSessionEmail(request)));
    }

    @PostMapping
    public ResponseEntity<?> reset(HttpServletRequest request, @Valid @RequestBody ResetRequest body) {
        reset.request(RequestUtil.getSessionEmail(request), body.password(), body.confirmation());
        restart.schedule();
        return ResponseEntity.accepted().body(Map.of("message", "Reset scheduled. The backend is restarting."));
    }
}
