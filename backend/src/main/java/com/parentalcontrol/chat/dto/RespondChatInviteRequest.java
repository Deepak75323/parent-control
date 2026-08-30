package com.parentalcontrol.chat.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RespondChatInviteRequest {

  @NotNull private Boolean accept;
}
