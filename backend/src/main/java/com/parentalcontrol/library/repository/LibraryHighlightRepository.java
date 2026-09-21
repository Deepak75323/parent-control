package com.parentalcontrol.library.repository;

import com.parentalcontrol.library.domain.LibraryHighlight;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LibraryHighlightRepository extends JpaRepository<LibraryHighlight, UUID> {

  List<LibraryHighlight> findByBookIdAndUserIdOrderByPageNumberAscCreatedAtAsc(
      UUID bookId, UUID userId);
}
