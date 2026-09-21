package com.parentalcontrol.config;

import com.parentalcontrol.common.constant.ApiConstants;
import com.parentalcontrol.common.domain.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final AdminEndpointProperties adminEndpointProperties;
  private final RestSecurityErrorHandlers restSecurityErrorHandlers;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.cors(cors -> {})
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(ApiConstants.PUBLIC_PATHS)
                    .permitAll()
                    // P2-02: API docs must not be anonymously reachable in any environment.
                    .requestMatchers(ApiConstants.DOCS_PATHS)
                    .hasAuthority(Role.ROLE_ADMIN.name())
                    .requestMatchers(HttpMethod.POST, ApiConstants.DEVICE_HEARTBEAT)
                    .hasAuthority(Role.ROLE_CHILD_DEVICE.name())
                    .requestMatchers(HttpMethod.POST, ApiConstants.DEVICE_TELEMETRY_ANT)
                    .hasAuthority(Role.ROLE_CHILD_DEVICE.name())
                    .requestMatchers(HttpMethod.POST, ApiConstants.DEVICE_ANALYTICS_ANT)
                    .hasAuthority(Role.ROLE_CHILD_DEVICE.name())
                    .requestMatchers(
                        ApiConstants.DEVICE_ANT, ApiConstants.CHILD_ANT, ApiConstants.EVENTS_ANT)
                    .hasAuthority(Role.ROLE_CHILD_DEVICE.name())
                    .requestMatchers(ApiConstants.MONITORING_SIGNALING_ANT)
                    .hasAnyAuthority(Role.ROLE_PARENT.name(), Role.ROLE_CHILD_DEVICE.name())
                    .requestMatchers(HttpMethod.GET, ApiConstants.MONITORING_WEBRTC_CONFIG)
                    .hasAnyAuthority(Role.ROLE_PARENT.name(), Role.ROLE_CHILD_DEVICE.name())
                    .requestMatchers(HttpMethod.POST, ApiConstants.MONITORING_WEBRTC_ICE_REPORT)
                    .hasAnyAuthority(Role.ROLE_PARENT.name(), Role.ROLE_CHILD_DEVICE.name())
                    .requestMatchers(
                        ApiConstants.DEVICES_ANT,
                        ApiConstants.MONITORING_ANT,
                        ApiConstants.ALERTS_ANT,
                        ApiConstants.POLICIES_ANT,
                        ApiConstants.AUDIT_ANT,
                        ApiConstants.PARENT_ANT,
                        ApiConstants.BILLING_ANT,
                        ApiConstants.TIME_EXTENSION_REQUESTS_ANT,
                        ApiConstants.CHAT_ANT,
                        ApiConstants.LIBRARY_ANT,
                        ApiConstants.AUTH_ACCOUNT)
                    .hasAuthority(Role.ROLE_PARENT.name())
                    .requestMatchers(ApiConstants.ADMIN_ANT)
                    .access(
                        (authentication, ctx) ->
                            adminEndpointProperties.isEndpointsEnabled()
                                ? new org.springframework.security.authorization
                                    .AuthorizationDecision(
                                    authentication.get().getAuthorities().stream()
                                        .anyMatch(
                                            a -> a.getAuthority().equals(Role.ROLE_ADMIN.name())))
                                : new org.springframework.security.authorization
                                    .AuthorizationDecision(false))
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(restSecurityErrorHandlers)
                    .accessDeniedHandler(restSecurityErrorHandlers))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }
}
