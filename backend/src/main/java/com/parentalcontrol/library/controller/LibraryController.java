package com.parentalcontrol.library.controller;

import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.common.constant.ApiConstants;
import com.parentalcontrol.library.dto.CreateBookmarkRequest;
import com.parentalcontrol.library.dto.CreateHighlightRequest;
import com.parentalcontrol.library.dto.LibraryBookResponse;
import com.parentalcontrol.library.dto.LibraryBookmarkResponse;
import com.parentalcontrol.library.dto.LibraryHighlightResponse;
import com.parentalcontrol.library.dto.LibraryProgressResponse;
import com.parentalcontrol.library.dto.UpdateBookRequest;
import com.parentalcontrol.library.dto.UpsertProgressRequest;
import com.parentalcontrol.library.service.LibraryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(ApiConstants.LIBRARY_BOOKS)
@RequiredArgsConstructor
@Tag(name = "Library", description = "Parent PDF library")
@SecurityRequirement(name = "bearerAuth")
public class LibraryController {

  private final LibraryService libraryService;

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Upload a PDF book")
  public ApiResponse<LibraryBookResponse> upload(
      @RequestPart("file") MultipartFile file,
      @RequestParam(value = "title", required = false) String title) {
    return ApiResponse.ok(libraryService.upload(file, title));
  }

  @GetMapping
  @Operation(summary = "List books visible to the current parent")
  public ApiResponse<List<LibraryBookResponse>> list(
      @RequestParam(value = "q", required = false) String query) {
    return ApiResponse.ok(libraryService.listBooks(query));
  }

  @GetMapping("/{bookId}")
  @Operation(summary = "Get book metadata")
  public ApiResponse<LibraryBookResponse> get(@PathVariable UUID bookId) {
    return ApiResponse.ok(libraryService.getBook(bookId));
  }

  @PutMapping("/{bookId}")
  @Operation(summary = "Update book metadata (owner only)")
  public ApiResponse<LibraryBookResponse> update(
      @PathVariable UUID bookId, @Valid @RequestBody UpdateBookRequest request) {
    return ApiResponse.ok(libraryService.updateBook(bookId, request));
  }

  @DeleteMapping("/{bookId}")
  @Operation(summary = "Delete a book (owner only)")
  public ApiResponse<Void> delete(@PathVariable UUID bookId) {
    libraryService.deleteBook(bookId);
    return ApiResponse.ok(null);
  }

  @GetMapping("/{bookId}/file")
  @Operation(summary = "Download/stream the PDF bytes")
  public ResponseEntity<Resource> file(@PathVariable UUID bookId) {
    Resource resource = libraryService.loadFile(bookId);
    return ResponseEntity.ok()
        .contentType(libraryService.fileMediaType())
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"book.pdf\"")
        .body(resource);
  }

  @GetMapping("/{bookId}/progress")
  @Operation(summary = "Get current user's reading progress")
  public ApiResponse<LibraryProgressResponse> getProgress(@PathVariable UUID bookId) {
    return ApiResponse.ok(libraryService.getProgress(bookId));
  }

  @PutMapping("/{bookId}/progress")
  @Operation(summary = "Save current user's reading progress")
  public ApiResponse<LibraryProgressResponse> putProgress(
      @PathVariable UUID bookId, @Valid @RequestBody UpsertProgressRequest request) {
    return ApiResponse.ok(libraryService.upsertProgress(bookId, request));
  }

  @GetMapping("/{bookId}/highlights")
  @Operation(summary = "List highlights for the current user")
  public ApiResponse<List<LibraryHighlightResponse>> listHighlights(@PathVariable UUID bookId) {
    return ApiResponse.ok(libraryService.listHighlights(bookId));
  }

  @PostMapping("/{bookId}/highlights")
  @Operation(summary = "Create a highlight")
  public ApiResponse<LibraryHighlightResponse> createHighlight(
      @PathVariable UUID bookId, @Valid @RequestBody CreateHighlightRequest request) {
    return ApiResponse.ok(libraryService.createHighlight(bookId, request));
  }

  @DeleteMapping("/{bookId}/highlights/{highlightId}")
  @Operation(summary = "Delete a highlight")
  public ApiResponse<Void> deleteHighlight(
      @PathVariable UUID bookId, @PathVariable UUID highlightId) {
    libraryService.deleteHighlight(bookId, highlightId);
    return ApiResponse.ok(null);
  }

  @GetMapping("/{bookId}/bookmarks")
  @Operation(summary = "List bookmarks for the current user")
  public ApiResponse<List<LibraryBookmarkResponse>> listBookmarks(@PathVariable UUID bookId) {
    return ApiResponse.ok(libraryService.listBookmarks(bookId));
  }

  @PostMapping("/{bookId}/bookmarks")
  @Operation(summary = "Create a bookmark")
  public ApiResponse<LibraryBookmarkResponse> createBookmark(
      @PathVariable UUID bookId, @Valid @RequestBody CreateBookmarkRequest request) {
    return ApiResponse.ok(libraryService.createBookmark(bookId, request));
  }

  @DeleteMapping("/{bookId}/bookmarks/{bookmarkId}")
  @Operation(summary = "Delete a bookmark")
  public ApiResponse<Void> deleteBookmark(
      @PathVariable UUID bookId, @PathVariable UUID bookmarkId) {
    libraryService.deleteBookmark(bookId, bookmarkId);
    return ApiResponse.ok(null);
  }
}
