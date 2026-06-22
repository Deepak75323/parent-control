package com.parentalcontrol.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final DeviceSecurityInterceptor deviceSecurityInterceptor;
    private final DeviceTrustInterceptor deviceTrustInterceptor;
    private final AuthRateLimitInterceptor authRateLimitInterceptor;

    @Bean
    public RepeatableBodyFilter repeatableBodyFilter() {
        return new RepeatableBodyFilter();
    }

    @Bean
    public FilterRegistrationBean<RepeatableBodyFilter> repeatableBodyFilterRegistration(
            RepeatableBodyFilter repeatableBodyFilter) {
        FilterRegistrationBean<RepeatableBodyFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(repeatableBodyFilter);
        registration.addUrlPatterns("/api/v1/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authRateLimitInterceptor)
                .addPathPatterns("/api/v1/auth/**", "/api/v1/device/pair");
        registry.addInterceptor(deviceSecurityInterceptor)
                .addPathPatterns(
                        "/api/v1/device/**",
                        "/api/v1/child/**",
                        "/api/v1/events/**",
                        "/api/v1/monitoring/signaling/**");
        registry.addInterceptor(deviceTrustInterceptor)
                .addPathPatterns(
                        "/api/v1/device/**",
                        "/api/v1/child/**",
                        "/api/v1/events/**",
                        "/api/v1/monitoring/signaling/**");
    }
}
