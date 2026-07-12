package com.parentalcontrol.auth.service;

import com.parentalcontrol.auth.dao.RefreshTokenDao;
import com.parentalcontrol.user.dao.UserDao;
import com.parentalcontrol.user.domain.User;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserTokenInvalidationService {

  private final UserDao userRepository;
  private final RefreshTokenDao refreshTokenRepository;

  @Transactional
  public void invalidateAllSessions(UUID userId) {
    User user = userRepository.findById(userId).orElse(null);
    if (Objects.isNull(user)) {
      return;
    }
    user.setTokensInvalidatedAfter(Instant.now());
    userRepository.save(user);
    refreshTokenRepository.revokeAllByUserId(userId);
  }

  public boolean isParentTokenRejected(UUID userId, Date issuedAt) {
    return userRepository
        .findById(userId)
        .map(user -> isTokenIssuedBeforeInvalidation(user, issuedAt))
        .orElse(true);
  }

  private static boolean isTokenIssuedBeforeInvalidation(User user, Date issuedAt) {
    if (user.getTokensInvalidatedAfter() == null || issuedAt == null) {
      return false;
    }
    return !issuedAt.toInstant().isAfter(user.getTokensInvalidatedAfter());
  }
}
