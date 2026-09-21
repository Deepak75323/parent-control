package com.parentalcontrol.library.dto;

import com.parentalcontrol.library.domain.LibraryBookStatus;
import com.parentalcontrol.library.domain.LibraryVisibility;
import java.time.Instant;
import java.util.UUID;

public record LibraryBookResponse(
    UUID id,
    String title,
    String originalFileName,
    String mimeType,
    long fileSizeBytes,
    Integer pageCount,
    LibraryVisibility visibility,
    LibraryBookStatus status,
    UUID uploadedBy,
    boolean ownedByMe,
    Instant createdAt,
    Instant updatedAt,
    LibraryProgressResponse progress) {}
