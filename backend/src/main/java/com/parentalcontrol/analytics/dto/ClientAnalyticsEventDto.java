package com.parentalcontrol.analytics.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.Data;

@Data
public class ClientAnalyticsEventDto {

  @NotBlank
  @Pattern(regexp = "impression|click|ingress|error|back")
  private String eventType;

  @NotBlank
  @Size(max = 128)
  private String section;

  @Size(max = 128)
  private String subSection;

  @Size(max = 256)
  private String eventLabel;

  @Size(max = 128)
  private String screen;

  private Long timestampMs;

  private Map<String, Object> attributes;
}
