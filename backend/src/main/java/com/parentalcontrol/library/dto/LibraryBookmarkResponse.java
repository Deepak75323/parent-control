package com.parentalcontrol.library.dto;

import java.time.Instant;
import java.util.UUID;

public record LibraryBookmarkResponse(
    UUID id, UUID bookId, int pageNumber, String label, Instant createdAt) {}
