package com.parentalcontrol.config;

import com.parentalcontrol.common.security.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthRateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/auth/") && "POST".equalsIgnoreCase(request.getMethod())) {
            String clientKey = resolveClientKey(request);
            rateLimitService.checkAuth(clientKey);
        }
        if ("/api/v1/device/pair".equals(path) && "POST".equalsIgnoreCase(request.getMethod())) {
            String clientKey = resolveClientKey(request);
            rateLimitService.checkPairing(clientKey);
        }
        return true;
    }

    private static String resolveClientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
