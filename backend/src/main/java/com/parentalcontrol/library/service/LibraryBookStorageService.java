package com.parentalcontrol.library.service;

import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.config.LibraryProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class LibraryBookStorageService {

  private static final Set<String> PDF_MIME_TYPES =
      Set.of("application/pdf", "application/x-pdf");

  private final LibraryProperties properties;

  public record ValidatedPdf(String mimeType, long sizeBytes, String originalFileName) {}

  public record StoredPdf(String storageKey, String contentHash, long sizeBytes) {}

  @PostConstruct
  void ensureStorageDirectory() {
    Path base = Path.of(properties.getStoragePath()).toAbsolutePath().normalize();
    try {
      Files.createDirectories(base);
      Path probe = base.resolve(".write-probe");
      Files.writeString(probe, "ok");
      Files.deleteIfExists(probe);
      log.info("Library book storage ready at {}", base);
    } catch (IOException e) {
      log.error("Library book storage is not usable at {}", base, e);
      throw new IllegalStateException("Library book storage is not writable", e);
    }
  }

  public ValidatedPdf validate(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR, MessageConstants.LIBRARY_PDF_REQUIRED);
    }
    String mimeType = normalizeMimeType(file.getContentType());
    if (!PDF_MIME_TYPES.contains(mimeType)) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.LIBRARY_PDF_TYPE_NOT_ALLOWED);
    }
    String name = sanitizeFileName(file.getOriginalFilename());
    if (!name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.LIBRARY_PDF_TYPE_NOT_ALLOWED);
    }
    long size = file.getSize();
    if (size > properties.getMaxPdfBytes()) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.LIBRARY_PDF_TOO_LARGE);
    }
    return new ValidatedPdf(mimeType, size, name);
  }

  public StoredPdf store(UUID ownerUserId, UUID bookId, MultipartFile file) {
    ValidatedPdf validated = validate(file);
    String storageKey = ownerUserId + "/" + bookId + ".pdf";
    Path target = resolvePath(storageKey);
    try {
      Files.createDirectories(target.getParent());
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (InputStream input = file.getInputStream();
          DigestInputStream digester = new DigestInputStream(input, digest)) {
        Files.copy(digester, target, StandardCopyOption.REPLACE_EXISTING);
      }
      // Magic-byte check after write
      assertPdfMagic(target);
      String hash = HexFormat.of().formatHex(digest.digest());
      long size = Files.size(target);
      return new StoredPdf(storageKey, hash, size);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    } catch (BusinessException e) {
      deleteQuietly(storageKey);
      throw e;
    } catch (IOException e) {
      deleteQuietly(storageKey);
      log.error("Failed to store library PDF {}", storageKey, e);
      throw new BusinessException(
          ErrorCode.INTERNAL_ERROR, MessageConstants.LIBRARY_PDF_STORE_FAILED);
    }
  }

  public Resource load(String storageKey) {
    Path path = resolvePath(storageKey);
    if (!Files.isRegularFile(path)) {
      throw new BusinessException(ErrorCode.NOT_FOUND, MessageConstants.LIBRARY_PDF_NOT_FOUND);
    }
    try {
      InputStream in = Files.newInputStream(path);
      return new InputStreamResource(in) {
        @Override
        public String getFilename() {
          return path.getFileName().toString();
        }

        @Override
        public long contentLength() throws IOException {
          return Files.size(path);
        }
      };
    } catch (IOException e) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, MessageConstants.LIBRARY_PDF_READ_FAILED);
    }
  }

  public MediaType mediaType() {
    return MediaType.APPLICATION_PDF;
  }

  public void deleteQuietly(String storageKey) {
    if (!StringUtils.hasText(storageKey)) {
      return;
    }
    try {
      Files.deleteIfExists(resolvePath(storageKey));
    } catch (IOException e) {
      log.warn("Could not delete library PDF {}", storageKey, e);
    }
  }

  private void assertPdfMagic(Path path) throws IOException {
    byte[] header = new byte[5];
    try (InputStream in = Files.newInputStream(path)) {
      int read = in.read(header);
      if (read < 5 || header[0] != '%' || header[1] != 'P' || header[2] != 'D' || header[3] != 'F') {
        throw new BusinessException(
            ErrorCode.VALIDATION_ERROR, MessageConstants.LIBRARY_PDF_CORRUPT);
      }
    }
  }

  private Path resolvePath(String storageKey) {
    Path base = Path.of(properties.getStoragePath()).toAbsolutePath().normalize();
    Path resolved = base.resolve(storageKey).normalize();
    if (!resolved.startsWith(base)) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid storage key");
    }
    return resolved;
  }

  private static String normalizeMimeType(String raw) {
    if (!StringUtils.hasText(raw)) {
      return "application/octet-stream";
    }
    String mime = raw.trim().toLowerCase(Locale.ROOT);
    int semi = mime.indexOf(';');
    return semi >= 0 ? mime.substring(0, semi).trim() : mime;
  }

  private static String sanitizeFileName(String original) {
    if (!StringUtils.hasText(original)) {
      return "book.pdf";
    }
    String name = Path.of(original).getFileName().toString().replaceAll("[\\\\/]+", "_");
    name = name.replaceAll("[^a-zA-Z0-9._\\- ()\\[\\]]+", "_");
    if (name.length() > 200) {
      name = name.substring(name.length() - 200);
    }
    return name.isBlank() ? "book.pdf" : name;
  }
}
