package gg.modl.backend.ai.service;

import gg.modl.backend.ai.LLMService;
import gg.modl.backend.infrastructure.authorization.RequiresPanelPermission;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequiresPanelPermission(view = "admin.settings.view", modify = "admin.settings.modify")
public class AIUsageController {
    private final AIUsageService usage;
    private final LLMService llm;

    @GetMapping("/v1/panel/settings/ai-usage")
    public Map<String, Object> getUsage(HttpServletRequest request) {
        return Map.of("usage", usage.getUsage(RequestUtil.getRequestServer(request)), "providerAvailable", llm.isAvailable());
    }
}
