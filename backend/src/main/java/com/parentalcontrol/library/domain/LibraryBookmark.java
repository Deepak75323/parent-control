package com.parentalcontrol.library.domain;

import com.parentalcontrol.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "library_bookmarks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LibraryBookmark extends BaseEntity {

  @Column(name = "book_id", nullable = false)
  private UUID bookId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "page_number", nullable = false)
  private int pageNumber;

  @Column(length = 255)
  private String label;
}
