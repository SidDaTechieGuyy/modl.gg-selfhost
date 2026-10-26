package gg.modl.backend.infrastructure.filter;

import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.server.ServerService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@RequiredArgsConstructor
public class FilterConfig {
    private final ServerService serverService;
    private final ApiKeyFilter apiKeyFilter;
    private final gg.modl.backend.infrastructure.origin.AllowedOrigins allowedOrigins;

    @Bean
    public FilterRegistrationBean<ServerHeaderFilter> serverDomainFilter() {
        final FilterRegistrationBean<ServerHeaderFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new ServerHeaderFilter(serverService, allowedOrigins));
        registrationBean.addUrlPatterns(RESTMappingV1.PREFIX_PANEL + "/*", RESTMappingV1.PREFIX_PUBLIC + "/*");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);

        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<ApiKeyFilter> apiKeyFilterRegistration() {
        final FilterRegistrationBean<ApiKeyFilter> registrationBean = new FilterRegistrationBean<>(apiKeyFilter);
        registrationBean.setEnabled(false);

        return registrationBean;
    }
}
