package com.parentalcontrol.library.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateBookmarkRequest(
    @NotNull @Min(1) Integer pageNumber, @Size(max = 255) String label) {}
