package gg.modl.backend.server.service;

import gg.modl.backend.server.data.Server;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PanelDomainResolver {


    @org.springframework.beans.factory.annotation.Value("${BASE_URL:http://localhost:8080}")
    private String baseUrl;
    public String panelDomain(Server server) { return java.net.URI.create(baseUrl).getAuthority(); }
    public String panelBaseUrl() { return baseUrl.replaceAll("/+$", ""); }
}
