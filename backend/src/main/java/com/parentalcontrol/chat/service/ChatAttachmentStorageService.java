package com.parentalcontrol.chat.service;

import com.parentalcontrol.chat.domain.ChatMessageType;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.config.ChatAttachmentProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatAttachmentStorageService {

  private static final Set<String> IMAGE_MIME_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
  private static final Set<String> VIDEO_MIME_TYPES =
      Set.of("video/mp4", "video/webm", "video/quicktime");

  private static final Map<String, String> MIME_EXTENSIONS =
      Map.of(
          "image/jpeg", ".jpg",
          "image/png", ".png",
          "image/webp", ".webp",
          "image/gif", ".gif",
          "video/mp4", ".mp4",
          "video/webm", ".webm",
          "video/quicktime", ".mov");

  private final ChatAttachmentProperties properties;

  public record StoredAttachment(
      ChatMessageType messageType,
      String storageKey,
      String mimeType,
      long sizeBytes,
      String originalFileName) {}

  @PostConstruct
  void ensureStorageDirectory() {
    Path base = Path.of(properties.getStoragePath()).toAbsolutePath().normalize();
    try {
      Files.createDirectories(base);
      Path probe = base.resolve(".write-probe");
      Files.writeString(probe, "ok");
      Files.deleteIfExists(probe);
      log.info("Chat attachment storage ready at {}", base);
    } catch (IOException e) {
      log.error("Chat attachment storage is not usable at {}", base, e);
      throw new IllegalStateException("Chat attachment storage is not writable", e);
    }
  }

  /** Validates upload metadata before any database writes. */
  public ValidatedAttachment validate(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_ATTACHMENT_REQUIRED);
    }

    String mimeType = normalizeMimeType(file.getContentType());
    ChatMessageType messageType = resolveMessageType(mimeType);
    long size = file.getSize();
    enforceSizeLimit(messageType, size);
    return new ValidatedAttachment(messageType, mimeType, size, sanitizeFileName(file.getOriginalFilename()));
  }

  public record ValidatedAttachment(
      ChatMessageType messageType, String mimeType, long sizeBytes, String originalFileName) {}

  public StoredAttachment store(
      UUID roomId, UUID messageId, MultipartFile file, ValidatedAttachment validated) {

    String extension = MIME_EXTENSIONS.getOrDefault(validated.mimeType(), "");
    String storageKey = roomId + "/" + messageId + extension;
    Path target = resolvePath(storageKey);

    try {
      Files.createDirectories(target.getParent());
      try (InputStream input = file.getInputStream()) {
        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException e) {
      log.warn(
          "Failed to store chat attachment roomId={} messageId={} path={}",
          roomId,
          messageId,
          target,
          e);
      throw new BusinessException(
          ErrorCode.INTERNAL_ERROR, MessageConstants.CHAT_ATTACHMENT_STORE_FAILED);
    }

    return new StoredAttachment(
        validated.messageType(),
        storageKey,
        validated.mimeType(),
        validated.sizeBytes(),
        validated.originalFileName());
  }

  public StoredAttachment store(UUID roomId, UUID messageId, MultipartFile file) {
    return store(roomId, messageId, file, validate(file));
  }

  public Resource loadAsResource(String storageKey) {
    Path path = resolvePath(storageKey);
    if (!Files.isRegularFile(path)) {
      throw new BusinessException(
          ErrorCode.NOT_FOUND, MessageConstants.CHAT_ATTACHMENT_NOT_FOUND);
    }
    try {
      return new InputStreamResource(Files.newInputStream(path));
    } catch (IOException e) {
      throw new BusinessException(
          ErrorCode.INTERNAL_ERROR, MessageConstants.CHAT_ATTACHMENT_READ_FAILED);
    }
  }

  /** Reads attachment bytes and removes the file from disk. */
  public byte[] readAndDelete(String storageKey) {
    Path path = resolvePath(storageKey);
    if (!Files.isRegularFile(path)) {
      throw new BusinessException(
          ErrorCode.NOT_FOUND, MessageConstants.CHAT_ATTACHMENT_NOT_FOUND);
    }
    try {
      byte[] bytes = Files.readAllBytes(path);
      Files.deleteIfExists(path);
      return bytes;
    } catch (IOException e) {
      throw new BusinessException(
          ErrorCode.INTERNAL_ERROR, MessageConstants.CHAT_ATTACHMENT_READ_FAILED);
    }
  }

  public MediaType mediaTypeFor(String mimeType) {
    if (!StringUtils.hasText(mimeType)) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
    try {
      return MediaType.parseMediaType(mimeType);
    } catch (Exception ignored) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
  }

  public void deleteQuietly(String storageKey) {
    if (!StringUtils.hasText(storageKey)) {
      return;
    }
    try {
      Files.deleteIfExists(resolvePath(storageKey));
    } catch (IOException ignored) {
      // Best-effort cleanup when rooms/messages are deleted.
    }
  }

  private void enforceSizeLimit(ChatMessageType messageType, long size) {
    long max =
        messageType == ChatMessageType.VIDEO
            ? properties.getMaxVideoBytes()
            : properties.getMaxImageBytes();
    if (size > max) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_ATTACHMENT_TOO_LARGE);
    }
  }

  private ChatMessageType resolveMessageType(String mimeType) {
    if (IMAGE_MIME_TYPES.contains(mimeType)) {
      return ChatMessageType.IMAGE;
    }
    if (VIDEO_MIME_TYPES.contains(mimeType)) {
      return ChatMessageType.VIDEO;
    }
    throw new BusinessException(
        ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_ATTACHMENT_TYPE_NOT_ALLOWED);
  }

  private String normalizeMimeType(String raw) {
    if (!StringUtils.hasText(raw)) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_ATTACHMENT_TYPE_NOT_ALLOWED);
    }
    return raw.toLowerCase(Locale.ROOT).split(";")[0].trim();
  }

  private Path resolvePath(String storageKey) {
    Path base = Path.of(properties.getStoragePath()).toAbsolutePath().normalize();
    Path resolved = base.resolve(storageKey).normalize();
    if (!resolved.startsWith(base)) {
      throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.CHAT_ATTACHMENT_NOT_FOUND);
    }
    return resolved;
  }

  private static String sanitizeFileName(String raw) {
    if (!StringUtils.hasText(raw)) {
      return "attachment";
    }
    String name = StringUtils.cleanPath(raw);
    int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
    if (slash >= 0 && slash < name.length() - 1) {
      name = name.substring(slash + 1);
    }
    if (name.length() > 200) {
      return name.substring(0, 200);
    }
    return name.isBlank() ? "attachment" : name;
  }
}
