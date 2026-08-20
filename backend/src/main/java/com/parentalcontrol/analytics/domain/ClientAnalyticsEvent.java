package com.parentalcontrol.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only client analytics row (screen / click / ingress / error / back). */
@Entity
@Table(name = "client_analytics_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientAnalyticsEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "actor_type", nullable = false, length = 32)
  private ClientAnalyticsActorType actorType;

  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "device_id")
  private UUID deviceId;

  @Column(name = "event_type", nullable = false, length = 32)
  private String eventType;

  @Column(nullable = false, length = 128)
  private String section;

  @Column(name = "sub_section", length = 128)
  private String subSection;

  @Column(name = "event_label", length = 256)
  private String eventLabel;

  @Column(length = 128)
  private String screen;

  @Column(name = "correlation_id", length = 64)
  private String correlationId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "attributes_json")
  private String attributesJson;

  @Column(name = "client_timestamp_ms", nullable = false)
  private long clientTimestampMs;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public static ClientAnalyticsEvent create(
      ClientAnalyticsActorType actorType,
      UUID userId,
      UUID deviceId,
      String eventType,
      String section,
      String subSection,
      String eventLabel,
      String screen,
      String correlationId,
      String attributesJson,
      long clientTimestampMs) {
    return ClientAnalyticsEvent.builder()
        .actorType(actorType)
        .userId(userId)
        .deviceId(deviceId)
        .eventType(eventType)
        .section(section)
        .subSection(subSection)
        .eventLabel(eventLabel)
        .screen(screen)
        .correlationId(correlationId)
        .attributesJson(attributesJson)
        .clientTimestampMs(clientTimestampMs)
        .createdAt(Instant.now())
        .build();
  }
}
