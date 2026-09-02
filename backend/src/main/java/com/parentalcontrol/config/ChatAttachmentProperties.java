package com.parentalcontrol.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.chat.attachments")
public class ChatAttachmentProperties {

  /** Local directory for uploaded chat media. */
  private String storagePath = "./data/chat-attachments";

  /** Max image upload size in bytes (default 10 MiB). */
  private long maxImageBytes = 10L * 1024 * 1024;

  /** Max video upload size in bytes (default 50 MiB). */
  private long maxVideoBytes = 50L * 1024 * 1024;
}
