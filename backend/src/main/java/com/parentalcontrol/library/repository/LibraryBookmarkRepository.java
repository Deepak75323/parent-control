package com.parentalcontrol.library.repository;

import com.parentalcontrol.library.domain.LibraryBookmark;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LibraryBookmarkRepository extends JpaRepository<LibraryBookmark, UUID> {

  List<LibraryBookmark> findByBookIdAndUserIdOrderByPageNumberAscCreatedAtAsc(
      UUID bookId, UUID userId);
}
