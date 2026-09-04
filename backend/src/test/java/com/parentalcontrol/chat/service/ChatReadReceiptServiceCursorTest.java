package com.parentalcontrol.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parentalcontrol.chat.domain.ChatMessage;
import com.parentalcontrol.chat.domain.ChatRoomMember;
import com.parentalcontrol.chat.repository.ChatMessageReceiptRepository;
import com.parentalcontrol.chat.repository.ChatMessageRepository;
import com.parentalcontrol.chat.repository.ChatRoomMemberRepository;
import com.parentalcontrol.user.dao.ParentProfileDao;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatReadReceiptServiceCursorTest {

  @Mock private ChatMessageReceiptRepository receiptRepository;
  @Mock private ChatMessageRepository messageRepository;
  @Mock private ChatRoomMemberRepository memberRepository;
  @Mock private ChatRealtimeService realtimeService;
  @Mock private ParentProfileDao parentProfileDao;

  @InjectMocks private ChatReadReceiptService readReceiptService;

  @Test
  void unreadUsesMessagesAfterLastReadCursor() {
    UUID viewer = UUID.randomUUID();
    UUID lastReadId = UUID.randomUUID();
    Instant t1 = Instant.parse("2026-09-04T01:00:00Z");
    Instant t2 = Instant.parse("2026-09-04T01:01:00Z");
    Instant t3 = Instant.parse("2026-09-04T01:02:00Z");

    ChatMessage cursor = message(lastReadId, UUID.randomUUID(), t1);
    ChatMessage a = message(UUID.randomUUID(), UUID.randomUUID(), t1);
    ChatMessage b = message(UUID.randomUUID(), UUID.randomUUID(), t2);
    ChatMessage c = message(UUID.randomUUID(), UUID.randomUUID(), t3);

    when(messageRepository.findById(lastReadId)).thenReturn(Optional.of(cursor));
    when(receiptRepository.findByMessageIdIn(any())).thenReturn(List.of());

    Map<UUID, Boolean> unread =
        readReceiptService.unreadForViewer(List.of(a, b, c), viewer, lastReadId, null);

    assertThat(unread.get(a.getId())).isFalse();
    assertThat(unread.get(b.getId())).isTrue();
    assertThat(unread.get(c.getId())).isTrue();
  }

  @Test
  void markUnreadMovesCursorToPreviousMessage() {
    UUID roomId = UUID.randomUUID();
    UUID viewer = UUID.randomUUID();
    UUID firstId = UUID.randomUUID();
    UUID secondId = UUID.randomUUID();
    Instant t1 = Instant.parse("2026-09-04T01:00:00Z");
    Instant t2 = Instant.parse("2026-09-04T01:01:00Z");

    ChatRoomMember member =
        ChatRoomMember.builder()
            .roomId(roomId)
            .userId(viewer)
            .lastReadMessageId(secondId)
            .build();
    when(memberRepository.findByRoomIdAndUserId(roomId, viewer)).thenReturn(Optional.of(member));

    ChatMessage first = message(firstId, UUID.randomUUID(), t1);
    first.setRoomId(roomId);
    ChatMessage second = message(secondId, UUID.randomUUID(), t2);
    second.setRoomId(roomId);
    when(messageRepository.findById(secondId)).thenReturn(Optional.of(second));
    when(messageRepository.findActiveByRoomId(roomId)).thenReturn(List.of(first, second));

    readReceiptService.markUnread(roomId, viewer, secondId);

    assertThat(member.getLastReadMessageId()).isEqualTo(firstId);
    verify(memberRepository).save(member);
  }

  @Test
  void markReadAdvancesCursorMonotonically() {
    UUID roomId = UUID.randomUUID();
    UUID viewer = UUID.randomUUID();
    UUID olderId = UUID.randomUUID();
    UUID newerId = UUID.randomUUID();
    Instant t1 = Instant.parse("2026-09-04T01:00:00Z");
    Instant t2 = Instant.parse("2026-09-04T01:01:00Z");

    ChatRoomMember member =
        ChatRoomMember.builder().roomId(roomId).userId(viewer).lastReadMessageId(newerId).build();
    when(memberRepository.findByRoomIdAndUserId(roomId, viewer)).thenReturn(Optional.of(member));

    ChatMessage older = message(olderId, UUID.randomUUID(), t1);
    older.setRoomId(roomId);
    ChatMessage newer = message(newerId, UUID.randomUUID(), t2);
    newer.setRoomId(roomId);
    when(messageRepository.findById(olderId)).thenReturn(Optional.of(older));
    when(messageRepository.findById(newerId)).thenReturn(Optional.of(newer));
    when(messageRepository.findActiveByRoomId(roomId)).thenReturn(List.of(older, newer));
    when(receiptRepository.markRead(eq(viewer), any(), any())).thenReturn(0);

    readReceiptService.markRead(roomId, viewer, olderId);

    assertThat(member.getLastReadMessageId()).isEqualTo(newerId);
  }

  private static ChatMessage message(UUID id, UUID sender, Instant createdAt) {
    ChatMessage message =
        ChatMessage.builder().senderUserId(sender).body("x").roomId(UUID.randomUUID()).build();
    message.setId(id);
    message.setCreatedAt(createdAt);
    return message;
  }
}
