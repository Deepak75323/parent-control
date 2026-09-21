package com.parentalcontrol.library.domain;

import com.parentalcontrol.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "library_reading_progress",
    uniqueConstraints = @UniqueConstraint(columnNames = {"book_id", "user_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LibraryReadingProgress extends BaseEntity {

  @Column(name = "book_id", nullable = false)
  private UUID bookId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "last_page", nullable = false)
  @Builder.Default
  private int lastPage = 1;

  @Column(name = "scroll_offset")
  private Double scrollOffset;

  @Column(name = "zoom_level")
  private Double zoomLevel;

  @Column(name = "progress_percent", nullable = false)
  @Builder.Default
  private double progressPercent = 0;

  @Column(name = "last_viewed_at", nullable = false)
  private Instant lastViewedAt;

  @Column(name = "completed_at")
  private Instant completedAt;
}
