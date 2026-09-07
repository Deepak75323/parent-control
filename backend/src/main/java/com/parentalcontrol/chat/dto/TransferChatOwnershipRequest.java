package com.parentalcontrol.chat.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

@Data
public class TransferChatOwnershipRequest {
  @NotNull private UUID userId;
}
