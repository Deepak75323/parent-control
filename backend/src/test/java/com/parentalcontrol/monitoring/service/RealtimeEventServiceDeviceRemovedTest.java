package com.parentalcontrol.monitoring.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.parentalcontrol.messaging.ClusterStompMessagingService;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RealtimeEventServiceDeviceRemovedTest {

  @Mock private ClusterStompMessagingService messagingTemplate;
  @Mock private ParentUserResolver parentUserResolver;
  @InjectMocks private RealtimeEventService realtimeEventService;

  @Test
  void publishDeviceRemovedFromParent_notifiesPreviousParentUser() {
    UUID previousProfileId = UUID.randomUUID();
    UUID previousUserId = UUID.randomUUID();
    UUID deviceId = UUID.randomUUID();
    when(parentUserResolver.resolveUserIdByProfileId(previousProfileId))
        .thenReturn(Optional.of(previousUserId));

    realtimeEventService.publishDeviceRemovedFromParent(previousProfileId, deviceId, "Kid Phone");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, String>> eventCaptor = ArgumentCaptor.forClass(Map.class);
    verify(messagingTemplate)
        .convertAndSendToUser(
            eq(previousUserId.toString()), eq("/queue/device-removed"), eventCaptor.capture());
    Map<String, String> event = eventCaptor.getValue();
    org.assertj.core.api.Assertions.assertThat(event.get("eventType")).isEqualTo("DEVICE_REASSIGNED");
    org.assertj.core.api.Assertions.assertThat(event.get("deviceId")).isEqualTo(deviceId.toString());
    org.assertj.core.api.Assertions.assertThat(event.get("deviceName")).isEqualTo("Kid Phone");
    org.assertj.core.api.Assertions.assertThat(event.get("status")).isEqualTo("REMOVED");
  }

  @Test
  void publishDeviceRemovedFromParent_skipsWhenPreviousParentUnknown() {
    UUID previousProfileId = UUID.randomUUID();
    when(parentUserResolver.resolveUserIdByProfileId(previousProfileId))
        .thenReturn(Optional.empty());

    realtimeEventService.publishDeviceRemovedFromParent(
        previousProfileId, UUID.randomUUID(), "Kid Phone");

    verifyNoInteractions(messagingTemplate);
  }
}
