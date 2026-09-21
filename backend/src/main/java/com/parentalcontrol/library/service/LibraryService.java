package com.parentalcontrol.library.service;

import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.friend.domain.ParentFriendship;
import com.parentalcontrol.friend.domain.ParentFriendshipStatus;
import com.parentalcontrol.friend.repository.ParentFriendshipRepository;
import com.parentalcontrol.library.domain.HighlightColor;
import com.parentalcontrol.library.domain.LibraryBook;
import com.parentalcontrol.library.domain.LibraryBookStatus;
import com.parentalcontrol.library.domain.LibraryBookmark;
import com.parentalcontrol.library.domain.LibraryHighlight;
import com.parentalcontrol.library.domain.LibraryReadingProgress;
import com.parentalcontrol.library.domain.LibraryVisibility;
import com.parentalcontrol.library.dto.CreateBookmarkRequest;
import com.parentalcontrol.library.dto.CreateHighlightRequest;
import com.parentalcontrol.library.dto.LibraryBookResponse;
import com.parentalcontrol.library.dto.LibraryBookmarkResponse;
import com.parentalcontrol.library.dto.LibraryHighlightResponse;
import com.parentalcontrol.library.dto.LibraryProgressResponse;
import com.parentalcontrol.library.dto.UpdateBookRequest;
import com.parentalcontrol.library.dto.UpsertProgressRequest;
import com.parentalcontrol.library.repository.LibraryBookRepository;
import com.parentalcontrol.library.repository.LibraryBookmarkRepository;
import com.parentalcontrol.library.repository.LibraryHighlightRepository;
import com.parentalcontrol.library.repository.LibraryReadingProgressRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class LibraryService {

  private final LibraryBookRepository bookRepository;
  private final LibraryReadingProgressRepository progressRepository;
  private final LibraryHighlightRepository highlightRepository;
  private final LibraryBookmarkRepository bookmarkRepository;
  private final LibraryBookStorageService storageService;
  private final ParentFriendshipRepository friendshipRepository;

  @Transactional
  public LibraryBookResponse upload(MultipartFile file, String titleOverride) {
    UUID userId = SecurityUtils.requireUserId();
    var validated = storageService.validate(file);

    LibraryBook pending =
        LibraryBook.builder()
            .ownerUserId(userId)
            .title(
                StringUtils.hasText(titleOverride)
                    ? titleOverride.trim()
                    : titleFromFileName(validated.originalFileName()))
            .originalFileName(validated.originalFileName())
            .storageKey("pending")
            .mimeType(validated.mimeType())
            .fileSizeBytes(validated.sizeBytes())
            .visibility(LibraryVisibility.PRIVATE)
            .status(LibraryBookStatus.READY)
            .build();
    pending = bookRepository.save(pending);
    final UUID pendingId = pending.getId();

    var stored = storageService.store(userId, pendingId, file);

    Optional<LibraryBook> duplicate =
        bookRepository
            .findByOwnerUserIdAndContentHash(userId, stored.contentHash())
            .filter(existing -> !existing.getId().equals(pendingId));
    if (duplicate.isPresent()) {
      storageService.deleteQuietly(stored.storageKey());
      bookRepository.delete(pending);
      throw new BusinessException(ErrorCode.CONFLICT, MessageConstants.LIBRARY_BOOK_DUPLICATE);
    }

    pending.setStorageKey(stored.storageKey());
    pending.setContentHash(stored.contentHash());
    pending.setFileSizeBytes(stored.sizeBytes());
    pending = bookRepository.save(pending);
    return toBookResponse(pending, userId, null);
  }

  @Transactional(readOnly = true)
  public List<LibraryBookResponse> listBooks(String query) {
    UUID userId = SecurityUtils.requireUserId();
    List<UUID> friendIds = acceptedFriendIds(userId);
    List<LibraryBook> books =
        bookRepository.findVisibleToUser(userId, friendIds, LibraryBookStatus.READY);
    String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    List<LibraryBookResponse> out = new ArrayList<>();
    for (LibraryBook book : books) {
      if (!q.isEmpty()
          && !book.getTitle().toLowerCase(Locale.ROOT).contains(q)
          && !book.getOriginalFileName().toLowerCase(Locale.ROOT).contains(q)) {
        continue;
      }
      LibraryProgressResponse progress =
          progressRepository
              .findByBookIdAndUserId(book.getId(), userId)
              .map(this::toProgressResponse)
              .orElse(null);
      out.add(toBookResponse(book, userId, progress));
    }
    return out;
  }

  @Transactional(readOnly = true)
  public LibraryBookResponse getBook(UUID bookId) {
    UUID userId = SecurityUtils.requireUserId();
    LibraryBook book = requireReadableBook(bookId, userId);
    LibraryProgressResponse progress =
        progressRepository
            .findByBookIdAndUserId(book.getId(), userId)
            .map(this::toProgressResponse)
            .orElse(null);
    return toBookResponse(book, userId, progress);
  }

  @Transactional
  public LibraryBookResponse updateBook(UUID bookId, UpdateBookRequest request) {
    UUID userId = SecurityUtils.requireUserId();
    LibraryBook book = requireOwnedBook(bookId, userId);
    if (StringUtils.hasText(request.title())) {
      book.setTitle(request.title().trim());
    }
    if (request.visibility() != null) {
      book.setVisibility(request.visibility());
    }
    if (request.pageCount() != null) {
      book.setPageCount(request.pageCount());
    }
    book = bookRepository.save(book);
    LibraryProgressResponse progress =
        progressRepository
            .findByBookIdAndUserId(book.getId(), userId)
            .map(this::toProgressResponse)
            .orElse(null);
    return toBookResponse(book, userId, progress);
  }

  @Transactional
  public void deleteBook(UUID bookId) {
    UUID userId = SecurityUtils.requireUserId();
    LibraryBook book = requireOwnedBook(bookId, userId);
    String key = book.getStorageKey();
    bookRepository.delete(book);
    storageService.deleteQuietly(key);
  }

  @Transactional(readOnly = true)
  public Resource loadFile(UUID bookId) {
    UUID userId = SecurityUtils.requireUserId();
    LibraryBook book = requireReadableBook(bookId, userId);
    return storageService.load(book.getStorageKey());
  }

  public MediaType fileMediaType() {
    return storageService.mediaType();
  }

  @Transactional(readOnly = true)
  public LibraryProgressResponse getProgress(UUID bookId) {
    UUID userId = SecurityUtils.requireUserId();
    requireReadableBook(bookId, userId);
    return progressRepository
        .findByBookIdAndUserId(bookId, userId)
        .map(this::toProgressResponse)
        .orElse(new LibraryProgressResponse(1, null, null, 0, null, null));
  }

  @Transactional
  public LibraryProgressResponse upsertProgress(UUID bookId, UpsertProgressRequest request) {
    UUID userId = SecurityUtils.requireUserId();
    LibraryBook book = requireReadableBook(bookId, userId);
    if (request.pageCount() != null
        && (book.getPageCount() == null || !book.getPageCount().equals(request.pageCount()))) {
      book.setPageCount(request.pageCount());
      bookRepository.save(book);
    }

    int lastPage = request.lastPage();
    Integer total = book.getPageCount();
    if (total != null && lastPage > total) {
      lastPage = total;
    }

    double percent = 0;
    Instant completedAt = null;
    if (total != null && total > 0) {
      percent = Math.min(100.0, (lastPage * 100.0) / total);
      if (lastPage >= total) {
        completedAt = Instant.now();
        percent = 100.0;
      }
    }

    Instant now = Instant.now();
    LibraryReadingProgress progress =
        progressRepository
            .findByBookIdAndUserId(bookId, userId)
            .orElseGet(
                () ->
                    LibraryReadingProgress.builder()
                        .bookId(bookId)
                        .userId(userId)
                        .lastViewedAt(now)
                        .build());
    progress.setLastPage(lastPage);
    progress.setScrollOffset(request.scrollOffset());
    progress.setZoomLevel(request.zoomLevel());
    progress.setProgressPercent(percent);
    progress.setLastViewedAt(now);
    if (completedAt != null) {
      progress.setCompletedAt(completedAt);
    } else if (total != null && lastPage < total) {
      progress.setCompletedAt(null);
    }
    return toProgressResponse(progressRepository.save(progress));
  }

  @Transactional(readOnly = true)
  public List<LibraryHighlightResponse> listHighlights(UUID bookId) {
    UUID userId = SecurityUtils.requireUserId();
    requireReadableBook(bookId, userId);
    return highlightRepository
        .findByBookIdAndUserIdOrderByPageNumberAscCreatedAtAsc(bookId, userId)
        .stream()
        .map(this::toHighlightResponse)
        .toList();
  }

  @Transactional
  public LibraryHighlightResponse createHighlight(UUID bookId, CreateHighlightRequest request) {
    UUID userId = SecurityUtils.requireUserId();
    requireReadableBook(bookId, userId);
    LibraryHighlight highlight =
        LibraryHighlight.builder()
            .bookId(bookId)
            .userId(userId)
            .pageNumber(request.pageNumber())
            .selectedText(request.selectedText().trim())
            .color(request.color() == null ? HighlightColor.YELLOW : request.color())
            .positionData(request.positionData())
            .visibility(
                request.visibility() == null ? LibraryVisibility.PRIVATE : request.visibility())
            .build();
    return toHighlightResponse(highlightRepository.save(highlight));
  }

  @Transactional
  public void deleteHighlight(UUID bookId, UUID highlightId) {
    UUID userId = SecurityUtils.requireUserId();
    requireReadableBook(bookId, userId);
    LibraryHighlight highlight =
        highlightRepository
            .findById(highlightId)
            .filter(h -> h.getBookId().equals(bookId) && h.getUserId().equals(userId))
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.NOT_FOUND, MessageConstants.LIBRARY_HIGHLIGHT_NOT_FOUND));
    highlightRepository.delete(highlight);
  }

  @Transactional(readOnly = true)
  public List<LibraryBookmarkResponse> listBookmarks(UUID bookId) {
    UUID userId = SecurityUtils.requireUserId();
    requireReadableBook(bookId, userId);
    return bookmarkRepository
        .findByBookIdAndUserIdOrderByPageNumberAscCreatedAtAsc(bookId, userId)
        .stream()
        .map(this::toBookmarkResponse)
        .toList();
  }

  @Transactional
  public LibraryBookmarkResponse createBookmark(UUID bookId, CreateBookmarkRequest request) {
    UUID userId = SecurityUtils.requireUserId();
    requireReadableBook(bookId, userId);
    LibraryBookmark bookmark =
        LibraryBookmark.builder()
            .bookId(bookId)
            .userId(userId)
            .pageNumber(request.pageNumber())
            .label(StringUtils.hasText(request.label()) ? request.label().trim() : null)
            .build();
    return toBookmarkResponse(bookmarkRepository.save(bookmark));
  }

  @Transactional
  public void deleteBookmark(UUID bookId, UUID bookmarkId) {
    UUID userId = SecurityUtils.requireUserId();
    requireReadableBook(bookId, userId);
    LibraryBookmark bookmark =
        bookmarkRepository
            .findById(bookmarkId)
            .filter(b -> b.getBookId().equals(bookId) && b.getUserId().equals(userId))
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.NOT_FOUND, MessageConstants.LIBRARY_BOOKMARK_NOT_FOUND));
    bookmarkRepository.delete(bookmark);
  }

  private LibraryBook requireOwnedBook(UUID bookId, UUID userId) {
    LibraryBook book =
        bookRepository
            .findById(bookId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.NOT_FOUND, MessageConstants.LIBRARY_BOOK_NOT_FOUND));
    if (!book.getOwnerUserId().equals(userId)) {
      throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.LIBRARY_BOOK_FORBIDDEN);
    }
    return book;
  }

  private LibraryBook requireReadableBook(UUID bookId, UUID userId) {
    LibraryBook book =
        bookRepository
            .findById(bookId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.NOT_FOUND, MessageConstants.LIBRARY_BOOK_NOT_FOUND));
    if (book.getOwnerUserId().equals(userId)) {
      return book;
    }
    if (book.getVisibility() == LibraryVisibility.SHARED
        && acceptedFriendIds(userId).contains(book.getOwnerUserId())) {
      return book;
    }
    throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.LIBRARY_BOOK_FORBIDDEN);
  }

  private List<UUID> acceptedFriendIds(UUID userId) {
    List<UUID> ids = new ArrayList<>();
    // Sentinel so JPQL IN () never receives an empty list
    ids.add(userId);
    for (ParentFriendship f :
        friendshipRepository.findAcceptedForUser(userId, ParentFriendshipStatus.ACCEPTED)) {
      UUID other =
          f.getRequesterUserId().equals(userId) ? f.getAddresseeUserId() : f.getRequesterUserId();
      ids.add(other);
    }
    return ids;
  }

  private static String titleFromFileName(String fileName) {
    String name = fileName;
    int dot = name.lastIndexOf('.');
    if (dot > 0) {
      name = name.substring(0, dot);
    }
    return name.replace('_', ' ').trim();
  }

  private LibraryBookResponse toBookResponse(
      LibraryBook book, UUID viewerId, LibraryProgressResponse progress) {
    return new LibraryBookResponse(
        book.getId(),
        book.getTitle(),
        book.getOriginalFileName(),
        book.getMimeType(),
        book.getFileSizeBytes(),
        book.getPageCount(),
        book.getVisibility(),
        book.getStatus(),
        book.getOwnerUserId(),
        book.getOwnerUserId().equals(viewerId),
        book.getCreatedAt(),
        book.getUpdatedAt(),
        progress);
  }

  private LibraryProgressResponse toProgressResponse(LibraryReadingProgress p) {
    return new LibraryProgressResponse(
        p.getLastPage(),
        p.getScrollOffset(),
        p.getZoomLevel(),
        p.getProgressPercent(),
        p.getLastViewedAt(),
        p.getCompletedAt());
  }

  private LibraryHighlightResponse toHighlightResponse(LibraryHighlight h) {
    return new LibraryHighlightResponse(
        h.getId(),
        h.getBookId(),
        h.getPageNumber(),
        h.getSelectedText(),
        h.getColor(),
        h.getPositionData(),
        h.getVisibility(),
        h.getCreatedAt(),
        h.getUpdatedAt());
  }

  private LibraryBookmarkResponse toBookmarkResponse(LibraryBookmark b) {
    return new LibraryBookmarkResponse(
        b.getId(), b.getBookId(), b.getPageNumber(), b.getLabel(), b.getCreatedAt());
  }
}
