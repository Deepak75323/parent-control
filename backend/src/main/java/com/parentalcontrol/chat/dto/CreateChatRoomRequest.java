package com.parentalcontrol.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateChatRoomRequest {

  @NotBlank
  @Size(min = 1, max = 64)
  private String name;
}
