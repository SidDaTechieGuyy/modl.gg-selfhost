package com.modl.selfhost.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppProperties {
    @Value("${app.base-domain:localhost}")
    private String baseDomain;

    @Value("${app.upload-dir:/app/uploads}")
    private String uploadDir;

    @Value("${app.cors-allowed-origins:*}")
    private String corsAllowedOrigins;

    public String getBaseDomain() {
        return baseDomain;
    }

    public String getUploadDir() {
        return uploadDir;
    }

    public String getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }
}
