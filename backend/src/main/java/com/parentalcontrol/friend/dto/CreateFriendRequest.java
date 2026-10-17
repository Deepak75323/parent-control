package com.parentalcontrol.friend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateFriendRequest {
  @NotBlank private String identifier;
}
