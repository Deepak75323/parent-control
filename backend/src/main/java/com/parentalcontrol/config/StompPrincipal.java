package com.parentalcontrol.config;

import com.parentalcontrol.common.domain.Role;
import java.security.Principal;
import java.util.UUID;

/** STOMP principal — {@link #getName()} returns the user UUID for /user/queue/* routing. */
public record StompPrincipal(UUID userId, String email, Role role, UUID deviceId)
    implements Principal {

  @Override
  public String getName() {
    return userId.toString();
  }
}
