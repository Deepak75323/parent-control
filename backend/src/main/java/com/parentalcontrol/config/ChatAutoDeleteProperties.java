package com.parentalcontrol.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.chat.auto-delete")
public class ChatAutoDeleteProperties {

  /**
   * How long to keep a message after every recipient has read it, before purging.
   * Prevents messages from vanishing mid-conversation when both parents are online.
   * Set to 0 to purge immediately on full read (not recommended for active chats).
   */
  private Duration graceAfterRead = Duration.ofMinutes(15);

  /** When false, never auto-delete on read (manual delete / clear still work). */
  private boolean enabled = true;
}
