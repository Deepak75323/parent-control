package com.parentalcontrol.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

@Data
public class ForgotPasswordRequest implements Serializable {
  @Serial private static final long serialVersionUID = 1L;

  @NotBlank @Email private String email;
}
