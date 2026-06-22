package com.parentalcontrol.auth.dto;

import com.parentalcontrol.user.dto.ParentProfileResponse;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresInSeconds;
    private ParentProfileResponse profile;
}
