package com.parentalcontrol.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.parentalcontrol.audit.service.AuditService;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.user.dao.UserDao;
import com.parentalcontrol.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class CameraPasswordServiceTest {

  @Mock private UserDao userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private AuditService auditService;

  private CameraPasswordService service;
  private MockedStatic<SecurityUtils> security;
  private final UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @BeforeEach
  void setUp() {
    service = new CameraPasswordService(userRepository, passwordEncoder, auditService);
    security = Mockito.mockStatic(SecurityUtils.class);
    security.when(SecurityUtils::requireUserId).thenReturn(userId);
  }

  @AfterEach
  void tearDown() {
    security.close();
  }

  @Test
  void createRequiresMatchingDigitsAndRejectsSecondCreate() {
    User user = User.builder().build();
    user.setId(userId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(passwordEncoder.encode("1234")).thenReturn("hash");

    assertThrows(BusinessException.class, () -> service.createPin("1234", "9999"));
    assertThrows(BusinessException.class, () -> service.createPin("12", "12"));

    service.createPin("1234", "1234");
    assertEquals("hash", user.getCameraPasswordHash());
    assertTrue(service.status().isPinConfigured());

    assertThrows(BusinessException.class, () -> service.createPin("5678", "5678"));
  }

  @Test
  void changeNeedsCurrentPassword() {
    User user = User.builder().cameraPasswordHash("old").build();
    user.setId(userId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("0000", "old")).thenReturn(false);
    when(passwordEncoder.matches("1111", "old")).thenReturn(true);
    when(passwordEncoder.encode("2222")).thenReturn("new");

    assertThrows(BusinessException.class, () -> service.changePin("0000", "2222", "2222"));

    service.changePin("1111", "2222", "2222");
    assertEquals("new", user.getCameraPasswordHash());
  }

  @Test
  void changeWithoutExistingPasswordFails() {
    User user = User.builder().build();
    user.setId(userId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    assertThrows(BusinessException.class, () -> service.changePin("1111", "2222", "2222"));
  }

  @Test
  void verifyIsOneShotAndRejectsWrongPassword() {
    User user = User.builder().cameraPasswordHash("hash").build();
    user.setId(userId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("1234", "hash")).thenReturn(true);
    when(passwordEncoder.matches("9999", "hash")).thenReturn(false);

    assertTrue(service.verifyPin("1234"));
    assertFalse(service.verifyPin("9999"));
  }

  @Test
  void verifyWithoutPasswordFails() {
    User user = User.builder().build();
    user.setId(userId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    assertThrows(BusinessException.class, () -> service.verifyPin("1234"));
    assertFalse(service.status().isPinConfigured());
  }
}
