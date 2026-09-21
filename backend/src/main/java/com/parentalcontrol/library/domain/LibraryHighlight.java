package com.parentalcontrol.library.domain;

import com.parentalcontrol.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "library_highlights")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LibraryHighlight extends BaseEntity {

  @Column(name = "book_id", nullable = false)
  private UUID bookId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "page_number", nullable = false)
  private int pageNumber;

  @Column(name = "selected_text", nullable = false, columnDefinition = "TEXT")
  private String selectedText;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  @Builder.Default
  private HighlightColor color = HighlightColor.YELLOW;

  /** JSON blob of PDF text-layer rects / anchors from the renderer. */
  @Column(name = "position_data", nullable = false, columnDefinition = "TEXT")
  private String positionData;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  @Builder.Default
  private LibraryVisibility visibility = LibraryVisibility.PRIVATE;
}
