package com.parentalcontrol.library.dto;

import com.parentalcontrol.library.domain.HighlightColor;
import com.parentalcontrol.library.domain.LibraryVisibility;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateHighlightRequest(
    @NotNull @Min(1) Integer pageNumber,
    @NotBlank @Size(max = 8000) String selectedText,
    HighlightColor color,
    @NotBlank @Size(max = 20000) String positionData,
    LibraryVisibility visibility) {}
