package com.parentalcontrol.library.dto;

import com.parentalcontrol.library.domain.HighlightColor;
import com.parentalcontrol.library.domain.LibraryVisibility;
import java.time.Instant;
import java.util.UUID;

public record LibraryHighlightResponse(
    UUID id,
    UUID bookId,
    int pageNumber,
    String selectedText,
    HighlightColor color,
    String positionData,
    LibraryVisibility visibility,
    Instant createdAt,
    Instant updatedAt) {}
