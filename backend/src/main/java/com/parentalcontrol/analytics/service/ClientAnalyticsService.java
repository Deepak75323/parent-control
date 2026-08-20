package com.parentalcontrol.analytics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parentalcontrol.analytics.domain.ClientAnalyticsActorType;
import com.parentalcontrol.analytics.domain.ClientAnalyticsEvent;
import com.parentalcontrol.analytics.dto.ClientAnalyticsBatchRequest;
import com.parentalcontrol.analytics.dto.ClientAnalyticsEventDto;
import com.parentalcontrol.analytics.repository.ClientAnalyticsEventRepository;
import com.parentalcontrol.common.security.SecurityUtils;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientAnalyticsService {

  private static final long MAX_FUTURE_SKEW_MS = 86_400_000L;

  private final ClientAnalyticsEventRepository repository;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;

  @Transactional
  public void ingestParentBatch(ClientAnalyticsBatchRequest batch) {
    UUID userId = SecurityUtils.requireUserId();
    ingestBatch(batch, ClientAnalyticsActorType.PARENT, userId, null);
  }

  @Transactional
  public void ingestDeviceBatch(ClientAnalyticsBatchRequest batch) {
    UUID deviceId = SecurityUtils.requireDeviceId();
    ingestBatch(batch, ClientAnalyticsActorType.CHILD_DEVICE, null, deviceId);
  }

  private void ingestBatch(
      ClientAnalyticsBatchRequest batch,
      ClientAnalyticsActorType actorType,
      UUID userId,
      UUID deviceId) {
    if (batch == null || batch.getEvents() == null || batch.getEvents().isEmpty()) {
      return;
    }
    String batchCorrelation =
        batch.getCorrelationId() != null && !batch.getCorrelationId().isBlank()
            ? batch.getCorrelationId()
            : null;
    for (ClientAnalyticsEventDto dto : batch.getEvents()) {
      if (dto == null || dto.getEventType() == null || dto.getSection() == null) {
        continue;
      }
      long ts = resolveTimestampMs(dto.getTimestampMs());
      String correlation = batchCorrelation;
      ClientAnalyticsEvent row =
          ClientAnalyticsEvent.create(
              actorType,
              userId,
              deviceId,
              dto.getEventType(),
              dto.getSection(),
              dto.getSubSection(),
              dto.getEventLabel(),
              dto.getScreen(),
              correlation,
              serializeAttributes(dto.getAttributes()),
              ts);
      repository.save(row);
      meterRegistry
          .counter(
              "client.analytics.events.total",
              "actor",
              actorType.name(),
              "event_type",
              dto.getEventType(),
              "section",
              dto.getSection())
          .increment();
    }
  }

  private long resolveTimestampMs(Long clientTs) {
    if (clientTs == null || clientTs <= 0) {
      return System.currentTimeMillis();
    }
    long now = System.currentTimeMillis();
    if (clientTs > now + MAX_FUTURE_SKEW_MS) {
      return now;
    }
    return clientTs;
  }

  private String serializeAttributes(Map<String, Object> attributes) {
    if (attributes == null || attributes.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(attributes);
    } catch (JsonProcessingException e) {
      log.debug("client analytics attributes serialization failed: {}", e.getMessage());
      return attributes.toString();
    }
  }
}
