package com.parentalcontrol.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.config.AccountLockoutProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginAttemptServiceTest {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOps;

  private AccountLockoutProperties properties;
  private LoginAttemptService service;

  private static final String EMAIL = "user@example.com";
  private static final String ATTEMPT_KEY = LoginAttemptService.ATTEMPT_KEY_PREFIX + EMAIL;
  private static final String LOCKED_KEY = LoginAttemptService.LOCKED_KEY_PREFIX + EMAIL;

  @BeforeEach
  void setUp() {
    properties = new AccountLockoutProperties();
    properties.setEnabled(true);
    properties.setMaxAttempts(5);
    properties.setAttemptWindowSeconds(900);
    properties.setLockDurationSeconds(900);
    properties.setFailClosed(false);

    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    service = new LoginAttemptService(properties, redisTemplate);
  }

  // -------------------------------------------------------------------------
  // assertNotLocked
  // -------------------------------------------------------------------------

  @Test
  void assertNotLocked_whenDisabled_doesNotCheckRedis() {
    properties.setEnabled(false);
    assertThatNoException().isThrownBy(() -> service.assertNotLocked(EMAIL));
    verify(redisTemplate, never()).hasKey(anyString());
  }

  @Test
  void assertNotLocked_whenKeyAbsent_doesNotThrow() {
    when(redisTemplate.hasKey(LOCKED_KEY)).thenReturn(false);
    assertThatNoException().isThrownBy(() -> service.assertNotLocked(EMAIL));
  }

  @Test
  void assertNotLocked_whenKeyPresent_throwsAccountLocked() {
    when(redisTemplate.hasKey(LOCKED_KEY)).thenReturn(true);
    assertThatThrownBy(() -> service.assertNotLocked(EMAIL))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.ACCOUNT_LOCKED));
  }

  @Test
  void assertNotLocked_whenRedisNull_andFailOpen_doesNotThrow() {
    service = new LoginAttemptService(properties, null);
    assertThatNoException().isThrownBy(() -> service.assertNotLocked(EMAIL));
  }

  @Test
  void assertNotLocked_whenRedisNull_andFailClosed_throwsAccountLocked() {
    properties.setFailClosed(true);
    service = new LoginAttemptService(properties, null);
    assertThatThrownBy(() -> service.assertNotLocked(EMAIL))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.ACCOUNT_LOCKED));
  }

  @Test
  void assertNotLocked_whenRedisThrows_andFailOpen_doesNotThrow() {
    when(redisTemplate.hasKey(LOCKED_KEY)).thenThrow(new RuntimeException("Redis down"));
    assertThatNoException().isThrownBy(() -> service.assertNotLocked(EMAIL));
  }

  @Test
  void assertNotLocked_whenRedisThrows_andFailClosed_throwsAccountLocked() {
    properties.setFailClosed(true);
    when(redisTemplate.hasKey(LOCKED_KEY)).thenThrow(new RuntimeException("Redis down"));
    assertThatThrownBy(() -> service.assertNotLocked(EMAIL))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.ACCOUNT_LOCKED));
  }

  // -------------------------------------------------------------------------
  // recordFailure
  // -------------------------------------------------------------------------

  @Test
  void recordFailure_whenDisabled_doesNothing() {
    properties.setEnabled(false);
    assertThatNoException().isThrownBy(() -> service.recordFailure(EMAIL));
    verify(valueOps, never()).increment(anyString());
  }

  @Test
  void recordFailure_firstAttempt_incrsCounterAndSetsTtl() {
    when(valueOps.increment(ATTEMPT_KEY)).thenReturn(1L);
    service.recordFailure(EMAIL);
    verify(valueOps).increment(ATTEMPT_KEY);
    verify(redisTemplate).expire(eq(ATTEMPT_KEY), eq(Duration.ofSeconds(900)));
  }

  @Test
  void recordFailure_subsequentAttempts_doNotResetTtl() {
    when(valueOps.increment(ATTEMPT_KEY)).thenReturn(3L);
    service.recordFailure(EMAIL);
    verify(redisTemplate, never()).expire(any(), any());
  }

  @Test
  void recordFailure_whenCountReachesMax_setsLockedKey() {
    properties.setMaxAttempts(3);
    when(valueOps.increment(ATTEMPT_KEY)).thenReturn(3L);
    service.recordFailure(EMAIL);
    verify(valueOps).set(eq(LOCKED_KEY), eq("1"), eq(Duration.ofSeconds(900)));
  }

  @Test
  void recordFailure_whenCountBelowMax_doesNotSetLockedKey() {
    properties.setMaxAttempts(5);
    when(valueOps.increment(ATTEMPT_KEY)).thenReturn(2L);
    service.recordFailure(EMAIL);
    verify(valueOps, never()).set(eq(LOCKED_KEY), anyString(), any(Duration.class));
  }

  @Test
  void recordFailure_whenRedisThrows_doesNotPropagateException() {
    when(valueOps.increment(anyString())).thenThrow(new RuntimeException("Redis down"));
    assertThatNoException().isThrownBy(() -> service.recordFailure(EMAIL));
  }

  // -------------------------------------------------------------------------
  // recordSuccess
  // -------------------------------------------------------------------------

  @Test
  void recordSuccess_deletesBothKeys() {
    when(redisTemplate.delete(ATTEMPT_KEY)).thenReturn(true);
    when(redisTemplate.delete(LOCKED_KEY)).thenReturn(false);
    service.recordSuccess(EMAIL);
    verify(redisTemplate).delete(ATTEMPT_KEY);
    verify(redisTemplate).delete(LOCKED_KEY);
  }

  @Test
  void recordSuccess_whenDisabled_doesNothing() {
    properties.setEnabled(false);
    assertThatNoException().isThrownBy(() -> service.recordSuccess(EMAIL));
    verify(redisTemplate, never()).delete(anyString());
  }

  @Test
  void recordSuccess_whenRedisThrows_doesNotPropagateException() {
    when(redisTemplate.delete(anyString())).thenThrow(new RuntimeException("Redis down"));
    assertThatNoException().isThrownBy(() -> service.recordSuccess(EMAIL));
  }

  // -------------------------------------------------------------------------
  // Error code metadata
  // -------------------------------------------------------------------------

  @Test
  void accountLockedErrorCode_isHttp429() {
    assertThat(ErrorCode.ACCOUNT_LOCKED.getStatus().value()).isEqualTo(429);
    assertThat(ErrorCode.ACCOUNT_LOCKED.getCode()).isEqualTo("ACCOUNT_LOCKED");
  }
}
