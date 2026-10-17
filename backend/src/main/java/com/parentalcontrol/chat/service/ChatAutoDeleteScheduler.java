package com.parentalcontrol.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatAutoDeleteScheduler {

  private final ChatService chatService;

  /** Purge messages that were fully read and whose grace period has elapsed. */
  @Scheduled(fixedDelayString = "${app.chat.auto-delete.sweep-interval-ms:60000}")
  public void sweepFullyReadMessages() {
    int purged = chatService.sweepAutoDeleteReadyMessages();
    if (purged > 0) {
      log.info("Auto-deleted {} fully-read chat message(s) after grace period", purged);
    }
    int wiped = chatService.wipeExpiredDeletedMessageBodies();
    if (wiped > 0) {
      log.info("Erased {} deleted chat body/bodies older than 24 hours", wiped);
    }
  }
}
