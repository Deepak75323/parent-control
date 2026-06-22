package com.parentalcontrol.monitoring.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TurnCredentialServiceTest {

  private static final String SECRET = "pcp_dev_turn_secret_32chars_minimum";

  @Test
  void computeCredential_isDeterministicForUsername() {
    String username = "1893456000:" + UUID.randomUUID();
    String a = TurnCredentialService.computeCredential(SECRET, username);
    String b = TurnCredentialService.computeCredential(SECRET, username);
    assertThat(a).isEqualTo(b);
    assertThat(a).isNotBlank();
  }

  @Test
  void computeCredential_differsForDifferentUsernames() {
    String userA = "1893456000:user-a";
    String userB = "1893456000:user-b";
    assertThat(TurnCredentialService.computeCredential(SECRET, userA))
        .isNotEqualTo(TurnCredentialService.computeCredential(SECRET, userB));
  }

  @Test
  void issue_embedsExpiryAndSubjectInUsername() {
    var props = new com.parentalcontrol.config.WebrtcProperties();
    props.getTurn().setSecret(SECRET);
    props.getTurn().setCredentialTtlSeconds(3600);
    props.getTurnUrls().add("turn:localhost:3478");

    var service = new TurnCredentialService(props);
    UUID subject = UUID.randomUUID();
    var cred = service.issue(subject);

    assertThat(cred.username()).contains(":" + subject);
    assertThat(cred.credential()).isNotBlank();
    assertThat(cred.expiresAtEpochSeconds()).isGreaterThan(System.currentTimeMillis() / 1000);
    assertThat(cred.ttlSeconds()).isEqualTo(3600);
  }

  @Test
  void issue_withSessionId_embedsSessionAndClampsTtl() {
    var props = new com.parentalcontrol.config.WebrtcProperties();
    props.getTurn().setSecret(SECRET);
    // Long configured TTL (1h) should be clamped to the session-bound ceiling (15min).
    props.getTurn().setCredentialTtlSeconds(3600);
    props.getTurnUrls().add("turn:localhost:3478");

    var service = new TurnCredentialService(props);
    UUID subject = UUID.randomUUID();
    UUID session = UUID.randomUUID();
    var cred = service.issue(subject, session);

    assertThat(cred.username()).contains(":" + subject + ":" + session);
    assertThat(cred.credential()).isNotBlank();
    // Session-bound TTL must be no longer than 15 minutes.
    assertThat(cred.ttlSeconds()).isLessThanOrEqualTo(15L * 60L);
    long maxExpected = System.currentTimeMillis() / 1000 + 15L * 60L + 5L;
    assertThat(cred.expiresAtEpochSeconds()).isLessThanOrEqualTo(maxExpected);
  }

  @Test
  void issue_withSessionId_distinctFromSubjectScopedCredential() {
    var props = new com.parentalcontrol.config.WebrtcProperties();
    props.getTurn().setSecret(SECRET);
    props.getTurn().setCredentialTtlSeconds(900);
    props.getTurnUrls().add("turn:localhost:3478");

    var service = new TurnCredentialService(props);
    UUID subject = UUID.randomUUID();
    UUID session = UUID.randomUUID();

    var subjectCred = service.issue(subject);
    var sessionCred = service.issue(subject, session);

    assertThat(sessionCred.username()).isNotEqualTo(subjectCred.username());
    assertThat(sessionCred.credential()).isNotEqualTo(subjectCred.credential());
  }
}
