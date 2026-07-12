package com.parentalcontrol.auth.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.parentalcontrol.auth.dao.EmailVerificationTokenDao;
import com.parentalcontrol.billing.service.SubscriptionService;
import com.parentalcontrol.config.EmailVerificationProperties;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.dao.UserDao;
import com.parentalcontrol.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

  @Mock private UserDao userRepository;
  @Mock private ParentProfileDao parentProfileRepository;
  @Mock private EmailVerificationTokenDao emailVerificationTokenRepository;
  @Mock private EmailVerificationProperties emailVerificationProperties;
  @Mock private EmailSender emailSender;
  @Mock private SubscriptionService subscriptionService;

  @InjectMocks private EmailVerificationService emailVerificationService;

  @Test
  void isVerifiedForParentProfileReturnsTrueWhenUserVerified() {
    UUID parentProfileId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setEmailVerified(true);

    when(parentProfileRepository.findUserIdByProfileId(parentProfileId))
        .thenReturn(Optional.of(userId));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    assertTrue(emailVerificationService.isVerifiedForParentProfile(parentProfileId));
  }

  @Test
  void isVerifiedForParentProfileReturnsFalseWhenUserUnverified() {
    UUID parentProfileId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setEmailVerified(false);

    when(parentProfileRepository.findUserIdByProfileId(parentProfileId))
        .thenReturn(Optional.of(userId));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    assertFalse(emailVerificationService.isVerifiedForParentProfile(parentProfileId));
  }
}
