package com.parentalcontrol.auth.dto;

import com.parentalcontrol.user.dto.ParentProfileResponse;
import java.io.Serial;
import java.io.Serializable;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse implements Serializable {

  @Serial private static final long serialVersionUID = 1L;
  private String accessToken;
  private String refreshToken;
  private String tokenType;
  private long expiresInSeconds;
  private ParentProfileResponse profile;
}
