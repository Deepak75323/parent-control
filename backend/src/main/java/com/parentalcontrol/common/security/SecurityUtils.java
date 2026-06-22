package com.parentalcontrol.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static Optional<AuthenticatedUser> currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static UUID requireUserId() {
        return currentUser()
                .map(AuthenticatedUser::getId)
                .orElseThrow(() -> new IllegalStateException("No authenticated user"));
    }

    public static Optional<UUID> getDeviceId() {
        return currentUser().map(AuthenticatedUser::getDeviceId);
    }

    public static UUID requireDeviceId() {
        return getDeviceId()
                .orElseThrow(() -> new IllegalStateException("No authenticated device"));
    }
}
