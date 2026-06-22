package com.parentalcontrol.config;

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

    private static final String[] PUBLIC_PATHS = {
            "/api/v1/auth/**",
            "/api/v1/device/pair",
            "/api/v1/billing/webhooks/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api-docs/**",
            "/v3/api-docs/**",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus",
            "/ws",
            "/ws/**",
            "/ws-native",
            "/ws-native/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/device/heartbeat")
                            .hasAuthority(Role.ROLE_CHILD_DEVICE.name())
                        .requestMatchers(HttpMethod.POST, "/api/v1/device/telemetry/**")
                            .hasAuthority(Role.ROLE_CHILD_DEVICE.name())
                        .requestMatchers("/api/v1/device/**", "/api/v1/child/**",
                                "/api/v1/events/**")
                            .hasAuthority(Role.ROLE_CHILD_DEVICE.name())
                        .requestMatchers("/api/v1/monitoring/signaling/**")
                            .hasAnyAuthority(Role.ROLE_PARENT.name(), Role.ROLE_CHILD_DEVICE.name())
                        .requestMatchers(HttpMethod.GET, "/api/v1/monitoring/webrtc-config")
                            .hasAnyAuthority(Role.ROLE_PARENT.name(), Role.ROLE_CHILD_DEVICE.name())
                        .requestMatchers(HttpMethod.POST, "/api/v1/monitoring/webrtc-ice-report")
                            .hasAnyAuthority(Role.ROLE_PARENT.name(), Role.ROLE_CHILD_DEVICE.name())
                        .requestMatchers("/api/v1/devices/**", "/api/v1/monitoring/**",
                                "/api/v1/alerts/**", "/api/v1/policies/**", "/api/v1/audit/**",
                                "/api/v1/parent/**", "/api/v1/billing/**",
                                "/api/v1/time-extension-requests/**")
                            .hasAuthority(Role.ROLE_PARENT.name())
                        .requestMatchers("/api/v1/admin/**")
                            .hasAuthority(Role.ROLE_ADMIN.name())
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/chaos/**")
                            .hasAuthority(Role.ROLE_ADMIN.name())
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/certification/**")
                            .hasAuthority(Role.ROLE_ADMIN.name())
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
