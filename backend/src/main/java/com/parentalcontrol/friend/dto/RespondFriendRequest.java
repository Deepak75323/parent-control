package com.parentalcontrol.friend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RespondFriendRequest {
  @NotNull private Boolean accept;
}
