package com.parentalcontrol.library.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.config.LibraryProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class LibraryBookStorageServiceTest {

  @TempDir Path tempDir;

  private LibraryBookStorageService storage;

  @BeforeEach
  void setUp() {
    LibraryProperties props = new LibraryProperties();
    props.setStoragePath(tempDir.toString());
    props.setMaxPdfBytes(1024 * 1024);
    storage = new LibraryBookStorageService(props);
    storage.ensureStorageDirectory();
  }

  @Test
  void rejectsNonPdfMime() {
    MockMultipartFile file =
        new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());
    assertThatThrownBy(() -> storage.validate(file)).isInstanceOf(BusinessException.class);
  }

  @Test
  void storesValidPdfAndComputesHash() throws Exception {
    byte[] pdf = "%PDF-1.4 minimal".getBytes(StandardCharsets.US_ASCII);
    MockMultipartFile file =
        new MockMultipartFile("file", "Java.pdf", "application/pdf", pdf);

    var stored =
        storage.store(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), file);

    assertThat(stored.contentHash()).hasSize(64);
    assertThat(Files.exists(tempDir.resolve(stored.storageKey()))).isTrue();
    assertThat(storage.load(stored.storageKey()).contentLength()).isEqualTo(pdf.length);
  }

  @Test
  void rejectsCorruptPdfMagic() {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "fake.pdf", "application/pdf", "NOTPDF".getBytes(StandardCharsets.US_ASCII));
    assertThatThrownBy(
            () -> storage.store(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), file))
        .isInstanceOf(BusinessException.class);
  }
}
