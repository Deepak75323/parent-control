package com.parentalcontrol.library.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpsertProgressRequest(
    @NotNull @Min(1) Integer lastPage,
    @Min(1) Integer pageCount,
    Double scrollOffset,
    @DecimalMin("0.25") @DecimalMax("4.0") Double zoomLevel) {}
