package com.parentalcontrol.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.parentalcontrol.chat.domain.ChatMessage;
import com.parentalcontrol.chat.domain.ChatMessageReceipt;
import com.parentalcontrol.chat.domain.ChatReceiptStatus;
import com.parentalcontrol.chat.repository.ChatMessageReceiptRepository;
import com.parentalcontrol.chat.repository.ChatMessageRepository;
import com.parentalcontrol.chat.repository.ChatRoomMemberRepository;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.domain.ParentProfile;
import com.parentalcontrol.user.domain.User;
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
class ChatReadReceiptServiceStatusPrivacyTest {

  @Mock private ChatMessageReceiptRepository receiptRepository;
  @Mock private ChatMessageRepository messageRepository;
  @Mock private ChatRoomMemberRepository memberRepository;
  @Mock private ChatRealtimeService realtimeService;
  @Mock private ParentProfileDao parentProfileDao;

  @InjectMocks private ChatReadReceiptService readReceiptService;

  @Test
  void statusStaysDeliveredWhenRecipientHasReadReceiptsDisabled() {
    UUID messageId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();
    UUID recipientId = UUID.randomUUID();

    stubMessage(messageId, senderId);
    stubFullyReadReceipt(messageId, recipientId);

    when(parentProfileDao.findByUser_Id(senderId)).thenReturn(Optional.of(profile(true)));
    when(parentProfileDao.findByUser_Id(recipientId)).thenReturn(Optional.of(profile(false)));

    assertThat(readReceiptService.statusForMessage(messageId))
        .isEqualTo(ChatReceiptStatus.DELIVERED);
  }

  @Test
  void statusStaysDeliveredWhenSenderHasReadReceiptsDisabled() {
    UUID messageId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();
    UUID recipientId = UUID.randomUUID();

    stubMessage(messageId, senderId);
    stubFullyReadReceipt(messageId, recipientId);

    when(parentProfileDao.findByUser_Id(senderId)).thenReturn(Optional.of(profile(false)));

    assertThat(readReceiptService.statusForMessage(messageId))
        .isEqualTo(ChatReceiptStatus.DELIVERED);
  }

  @Test
  void statusIsReadWhenBothSidesHaveReadReceiptsEnabled() {
    UUID messageId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();
    UUID recipientId = UUID.randomUUID();

    stubMessage(messageId, senderId);
    stubFullyReadReceipt(messageId, recipientId);

    when(parentProfileDao.findByUser_Id(senderId)).thenReturn(Optional.of(profile(true)));
    when(parentProfileDao.findByUser_Id(recipientId)).thenReturn(Optional.of(profile(true)));

    assertThat(readReceiptService.statusForMessage(messageId)).isEqualTo(ChatReceiptStatus.READ);
  }

  private void stubMessage(UUID messageId, UUID senderId) {
    ChatMessage message = ChatMessage.builder().senderUserId(senderId).body("hi").build();
    message.setId(messageId);
    when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
  }

  private void stubFullyReadReceipt(UUID messageId, UUID recipientId) {
    ChatMessageReceipt receipt =
        ChatMessageReceipt.builder()
            .messageId(messageId)
            .recipientUserId(recipientId)
            .build();
    receipt.setDeliveredAt(Instant.now());
    receipt.setReadAt(Instant.now());
    when(receiptRepository.findByMessageId(messageId)).thenReturn(List.of(receipt));
  }

  private static ParentProfile profile(boolean readReceiptsEnabled) {
    return ParentProfile.builder()
        .user(User.builder().build())
        .readReceiptsEnabled(readReceiptsEnabled)
        .build();
  }
}
