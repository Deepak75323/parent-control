package com.parentalcontrol.monitoring.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InstalledAppItemRequest {

  @NotBlank private String packageName;

  @NotBlank private String appLabel;

  private String appCategory;

  /** Optional small PNG as base64 (no data-URL prefix). Max ~24KB decoded. */
  @Size(max = 32768)
  private String iconBase64;
}
