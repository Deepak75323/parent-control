package com.parentalcontrol.user.dto;

import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParentProfileResponse {
  private UUID id;
  private String firstName;
  private String lastName;
  private String email;
  private String phone;
  private String timezone;
  private boolean emailVerified;
}
