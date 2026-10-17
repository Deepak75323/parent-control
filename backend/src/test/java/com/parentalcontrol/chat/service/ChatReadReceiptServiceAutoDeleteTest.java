package com.parentalcontrol.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parentalcontrol.chat.domain.ChatMessage;
import com.parentalcontrol.chat.domain.ChatMessageReceipt;
import com.parentalcontrol.chat.domain.ChatRoomMember;
import com.parentalcontrol.chat.repository.ChatMessageReceiptRepository;
import com.parentalcontrol.chat.repository.ChatMessageRepository;
import com.parentalcontrol.chat.repository.ChatRoomMemberRepository;
import com.parentalcontrol.user.dao.ParentProfileDao;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatReadReceiptServiceAutoDeleteTest {

  @Mock private ChatMessageReceiptRepository receiptRepository;
  @Mock private ChatMessageRepository messageRepository;
  @Mock private ChatRoomMemberRepository memberRepository;
  @Mock private ChatRealtimeService realtimeService;
  @Mock private ParentProfileDao parentProfileDao;

  @InjectMocks private ChatReadReceiptService readReceiptService;

  @Test
  void isFullyReadRequiresAtLeastOneReceipt() {
    UUID messageId = UUID.randomUUID();
    when(receiptRepository.findByMessageId(messageId)).thenReturn(List.of());

    assertThat(readReceiptService.isFullyReadByAllRecipients(messageId)).isFalse();
  }

  @Test
  void isFullyReadWhenEveryRecipientHasReadAt() {
    UUID messageId = UUID.randomUUID();
    ChatMessageReceipt one =
        ChatMessageReceipt.builder()
            .messageId(messageId)
            .recipientUserId(UUID.randomUUID())
            .build();
    one.setReadAt(Instant.now());
    ChatMessageReceipt two =
        ChatMessageReceipt.builder()
            .messageId(messageId)
            .recipientUserId(UUID.randomUUID())
            .build();
    two.setReadAt(Instant.now());
    when(receiptRepository.findByMessageId(messageId)).thenReturn(List.of(one, two));

    assertThat(readReceiptService.isFullyReadByAllRecipients(messageId)).isTrue();
  }

  @Test
  void isFullyReadFalseWhenAnyRecipientUnread() {
    UUID messageId = UUID.randomUUID();
    ChatMessageReceipt read =
        ChatMessageReceipt.builder()
            .messageId(messageId)
            .recipientUserId(UUID.randomUUID())
            .build();
    read.setReadAt(Instant.now());
    ChatMessageReceipt unread =
        ChatMessageReceipt.builder()
            .messageId(messageId)
            .recipientUserId(UUID.randomUUID())
            .build();
    when(receiptRepository.findByMessageId(messageId)).thenReturn(List.of(read, unread));

    assertThat(readReceiptService.isFullyReadByAllRecipients(messageId)).isFalse();
  }

  @Test
  void markReadReturnsFullyReadMessageIds() {
    UUID roomId = UUID.randomUUID();
    UUID recipientId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();
    UUID messageId = UUID.randomUUID();
    Instant created = Instant.now();

    when(memberRepository.findByRoomIdAndUserId(roomId, recipientId))
        .thenReturn(
            Optional.of(
                ChatRoomMember.builder().roomId(roomId).userId(recipientId).build()));

    ChatMessage message =
        ChatMessage.builder()
            .roomId(roomId)
            .senderUserId(senderId)
            .body("hi")
            .build();
    // id + createdAt via reflection-friendly setters if builder doesn't set id
    message.setId(messageId);
    message.setCreatedAt(created);

    when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
    when(messageRepository.findActiveByRoomId(roomId)).thenReturn(List.of(message));
    when(receiptRepository.markRead(eq(recipientId), any(), any())).thenReturn(1);

    ChatMessageReceipt receipt =
        ChatMessageReceipt.builder()
            .messageId(messageId)
            .recipientUserId(recipientId)
            .build();
    receipt.setReadAt(Instant.now());
    when(receiptRepository.findByMessageId(messageId)).thenReturn(List.of(receipt));
    when(parentProfileDao.findByUser_Id(recipientId)).thenReturn(Optional.empty());
    when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));

    List<UUID> fullyRead = readReceiptService.markRead(roomId, recipientId, messageId);

    assertThat(fullyRead).containsExactly(messageId);
    verify(realtimeService)
        .publishReceiptUpdated(eq(senderId), eq(roomId), eq(messageId), any());
  }

  @Test
  void markReadDoesNotReturnIdsWhenOtherRecipientStillUnread() {
    UUID roomId = UUID.randomUUID();
    UUID recipientId = UUID.randomUUID();
    UUID otherRecipient = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();
    UUID messageId = UUID.randomUUID();
    Instant created = Instant.now();

    when(memberRepository.findByRoomIdAndUserId(roomId, recipientId))
        .thenReturn(
            Optional.of(
                ChatRoomMember.builder().roomId(roomId).userId(recipientId).build()));

    ChatMessage message =
        ChatMessage.builder().roomId(roomId).senderUserId(senderId).body("hi").build();
    message.setId(messageId);
    message.setCreatedAt(created);

    when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
    when(messageRepository.findActiveByRoomId(roomId)).thenReturn(List.of(message));
    when(receiptRepository.markRead(eq(recipientId), any(), any())).thenReturn(1);

    ChatMessageReceipt mine =
        ChatMessageReceipt.builder()
            .messageId(messageId)
            .recipientUserId(recipientId)
            .build();
    mine.setReadAt(Instant.now());
    ChatMessageReceipt other =
        ChatMessageReceipt.builder()
            .messageId(messageId)
            .recipientUserId(otherRecipient)
            .build();
    when(receiptRepository.findByMessageId(messageId)).thenReturn(List.of(mine, other));

    List<UUID> fullyRead = readReceiptService.markRead(roomId, recipientId, messageId);

    assertThat(fullyRead).isEmpty();
  }

  @Test
  void markReadSetsLastReadCursorAndClearsLegacyUnreadFrom() {
    UUID roomId = UUID.randomUUID();
    UUID recipientId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();
    UUID messageId = UUID.randomUUID();
    Instant created = Instant.now();

    ChatRoomMember member =
        ChatRoomMember.builder()
            .roomId(roomId)
            .userId(recipientId)
            .unreadFromAt(created)
            .build();
    when(memberRepository.findByRoomIdAndUserId(roomId, recipientId))
        .thenReturn(Optional.of(member));

    ChatMessage message =
        ChatMessage.builder().roomId(roomId).senderUserId(senderId).body("hi").build();
    message.setId(messageId);
    message.setCreatedAt(created);
    when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
    when(messageRepository.findActiveByRoomId(roomId)).thenReturn(List.of(message));
    when(receiptRepository.markRead(eq(recipientId), any(), any())).thenReturn(0);

    readReceiptService.markRead(roomId, recipientId, messageId);

    assertThat(member.getLastReadMessageId()).isEqualTo(messageId);
    assertThat(member.getUnreadFromAt()).isNull();
    verify(memberRepository).save(member);
  }

  @Test
  void isReadyForAutoDeleteRespectsGracePeriod() {
    UUID messageId = UUID.randomUUID();
    ChatMessageReceipt receipt =
        ChatMessageReceipt.builder()
            .messageId(messageId)
            .recipientUserId(UUID.randomUUID())
            .build();
    receipt.setReadAt(Instant.now());
    when(receiptRepository.findByMessageId(messageId)).thenReturn(List.of(receipt));

    assertThat(readReceiptService.isReadyForAutoDelete(messageId, java.time.Duration.ofMinutes(15)))
        .isFalse();
    assertThat(readReceiptService.isReadyForAutoDelete(messageId, java.time.Duration.ZERO))
        .isTrue();
  }

  @Test
  void isReadyForAutoDeleteWhenGraceAlreadyElapsed() {
    UUID messageId = UUID.randomUUID();
    ChatMessageReceipt receipt =
        ChatMessageReceipt.builder()
            .messageId(messageId)
            .recipientUserId(UUID.randomUUID())
            .build();
    receipt.setReadAt(Instant.now().minus(java.time.Duration.ofMinutes(20)));
    when(receiptRepository.findByMessageId(messageId)).thenReturn(List.of(receipt));

    assertThat(readReceiptService.isReadyForAutoDelete(messageId, java.time.Duration.ofMinutes(15)))
        .isTrue();
  }
}
