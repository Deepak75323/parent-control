package com.parentalcontrol.library.repository;

import com.parentalcontrol.library.domain.LibraryReadingProgress;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LibraryReadingProgressRepository
    extends JpaRepository<LibraryReadingProgress, UUID> {

  Optional<LibraryReadingProgress> findByBookIdAndUserId(UUID bookId, UUID userId);
}
