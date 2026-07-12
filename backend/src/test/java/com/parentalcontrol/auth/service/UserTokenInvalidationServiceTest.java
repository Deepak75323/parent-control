package com.parentalcontrol.auth.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.parentalcontrol.auth.dao.RefreshTokenDao;
import com.parentalcontrol.user.dao.UserDao;
import com.parentalcontrol.user.domain.User;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserTokenInvalidationServiceTest {

  @Mock private UserDao userRepository;
  @Mock private RefreshTokenDao refreshTokenRepository;

  @InjectMocks private UserTokenInvalidationService service;

  @Test
  void rejectsAccessTokenIssuedBeforeInvalidation() {
    UUID userId = UUID.randomUUID();
    Instant invalidatedAt = Instant.parse("2026-07-11T12:00:00Z");
    User user = new User();
    user.setTokensInvalidatedAfter(invalidatedAt);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    Date issuedAt = Date.from(Instant.parse("2026-07-11T11:59:00Z"));
    assertTrue(service.isParentTokenRejected(userId, issuedAt));
  }

  @Test
  void acceptsAccessTokenIssuedAfterInvalidation() {
    UUID userId = UUID.randomUUID();
    Instant invalidatedAt = Instant.parse("2026-07-11T12:00:00Z");
    User user = new User();
    user.setTokensInvalidatedAfter(invalidatedAt);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    Date issuedAt = Date.from(Instant.parse("2026-07-11T12:00:01Z"));
    assertFalse(service.isParentTokenRejected(userId, issuedAt));
  }
}
