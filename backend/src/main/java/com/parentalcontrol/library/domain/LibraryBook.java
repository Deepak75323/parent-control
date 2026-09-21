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
@Table(name = "library_books")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LibraryBook extends BaseEntity {

  @Column(name = "owner_user_id", nullable = false)
  private UUID ownerUserId;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(name = "original_file_name", nullable = false, length = 255)
  private String originalFileName;

  @Column(name = "storage_key", nullable = false, length = 512)
  private String storageKey;

  @Column(name = "mime_type", nullable = false, length = 128)
  @Builder.Default
  private String mimeType = "application/pdf";

  @Column(name = "file_size_bytes", nullable = false)
  private long fileSizeBytes;

  @Column(name = "page_count")
  private Integer pageCount;

  @Column(name = "content_hash", length = 64)
  private String contentHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  @Builder.Default
  private LibraryVisibility visibility = LibraryVisibility.PRIVATE;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  @Builder.Default
  private LibraryBookStatus status = LibraryBookStatus.READY;
}
