package com.parentalcontrol.library.dto;

import com.parentalcontrol.library.domain.LibraryVisibility;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateBookRequest(
    @Size(max = 255) String title,
    LibraryVisibility visibility,
    @Min(1) Integer pageCount) {}
