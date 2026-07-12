package com.parentalcontrol.config;

import com.parentalcontrol.common.constant.ApiConstants;
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
    registration.addUrlPatterns(ApiConstants.API_V1_SERVLET);
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(authRateLimitInterceptor)
        .addPathPatterns(
            ApiConstants.AUTH_ANT,
            ApiConstants.DEVICE_PAIR,
            ApiConstants.ONBOARDING_ANT,
            ApiConstants.BILLING_WEBHOOKS_ANT,
            ApiConstants.DEVICES_PAIRING_CODE);
    registry
        .addInterceptor(deviceSecurityInterceptor)
        .addPathPatterns(
            ApiConstants.DEVICE_ANT,
            ApiConstants.CHILD_ANT,
            ApiConstants.EVENTS_ANT,
            ApiConstants.MONITORING_SIGNALING_ANT);
    registry
        .addInterceptor(deviceTrustInterceptor)
        .addPathPatterns(
            ApiConstants.DEVICE_ANT,
            ApiConstants.CHILD_ANT,
            ApiConstants.EVENTS_ANT,
            ApiConstants.MONITORING_SIGNALING_ANT);
  }
}
