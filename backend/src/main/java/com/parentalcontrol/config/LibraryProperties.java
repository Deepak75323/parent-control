package com.parentalcontrol.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.library")
public class LibraryProperties {

  /** Local directory for uploaded library PDFs. */
  private String storagePath = "./data/library-books";

  /** Max PDF upload size in bytes (default 100 MiB). */
  private long maxPdfBytes = 100L * 1024 * 1024;
}
