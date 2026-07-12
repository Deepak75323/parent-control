package com.parentalcontrol.observability.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.Data;

@Data
public class MobileTelemetryEventDto {

  @NotBlank
  @Size(max = 64)
  private String category;

  @NotBlank
  @Size(max = 128)
  private String event;

  @Min(0)
  @Max(4102444800000L)
  private Long timestampMs;

  @Size(max = 32)
  private Map<String, Object> attributes;
}
