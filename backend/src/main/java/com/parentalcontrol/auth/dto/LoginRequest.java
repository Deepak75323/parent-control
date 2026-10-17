package com.parentalcontrol.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

@Data
public class LoginRequest implements Serializable {

  @Serial private static final long serialVersionUID = 1L;
  @NotBlank @Email private String email;

  /** Login accepts any non-empty password; registration still enforces min length 8. */
  @NotBlank
  @Size(min = 1, max = 128)
  private String password;
}
