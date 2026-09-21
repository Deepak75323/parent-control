package com.parentalcontrol.library.dto;

import java.time.Instant;

public record LibraryProgressResponse(
    int lastPage,
    Double scrollOffset,
    Double zoomLevel,
    double progressPercent,
    Instant lastViewedAt,
    Instant completedAt) {}
