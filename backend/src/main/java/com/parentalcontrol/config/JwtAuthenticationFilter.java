package com.parentalcontrol.config;

import com.parentalcontrol.common.domain.Role;
import com.parentalcontrol.common.domain.TokenType;
import com.parentalcontrol.common.security.AuthenticatedUser;
import com.parentalcontrol.common.security.JwtTokenProvider;
import com.parentalcontrol.device.security.DeviceTokenValidationService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_EXACT = Set.of(
            "/api/v1/device/pair"
    );
    private static final Set<String> PUBLIC_PREFIX = Set.of(
            "/api/v1/billing/webhooks/",
            "/api/v1/auth/",
            "/swagger-ui",
            "/api-docs",
            "/v3/api-docs",
            "/actuator/health",
            "/actuator/info",
            "/ws",
            "/ws/"
    );

    private final JwtTokenProvider jwtTokenProvider;
    private final DeviceTokenValidationService deviceTokenValidationService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (isPublic(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        if (!jwtTokenProvider.validateToken(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        Claims claims = jwtTokenProvider.parseClaims(token);
        TokenType tokenType = jwtTokenProvider.getTokenType(claims);

        if (path.startsWith("/api/v1/device/") && tokenType != TokenType.DEVICE) {
            filterChain.doFilter(request, response);
            return;
        }
        if (tokenType == TokenType.DEVICE) {
            try {
                deviceTokenValidationService.validateDeviceToken(claims);
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }
        if (isParentMonitoringPath(path) && tokenType != TokenType.ACCESS) {
            filterChain.doFilter(request, response);
            return;
        }

        AuthenticatedUser principal = AuthenticatedUser.builder()
                .id(jwtTokenProvider.getUserId(claims))
                .email(claims.get("email", String.class))
                .role(jwtTokenProvider.getRole(claims))
                .deviceId(jwtTokenProvider.getDeviceId(claims))
                .build();

        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private boolean isPublic(String path) {
        if (PUBLIC_EXACT.contains(path)) {
            return true;
        }
        return PUBLIC_PREFIX.stream().anyMatch(path::startsWith);
    }

    /** Parent-only monitoring paths; signaling + webrtc-config are shared with child device tokens. */
    private boolean isParentMonitoringPath(String path) {
        if (path.startsWith("/api/v1/monitoring/signaling/")) {
            return false;
        }
        if ("/api/v1/monitoring/webrtc-config".equals(path)) {
            return false;
        }
        if ("/api/v1/monitoring/webrtc-ice-report".equals(path)) {
            return false;
        }
        return path.startsWith("/api/v1/devices")
                || path.startsWith("/api/v1/monitoring")
                || path.startsWith("/api/v1/policies");
    }
}
