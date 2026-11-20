package com.parentalcontrol.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatAutoGreetingScheduler {

  private final ChatAutoGreetingService chatAutoGreetingService;

  @Scheduled(fixedDelayString = "${app.chat.auto-greeting.sweep-interval-ms:60000}")
  public void sweepAutoGreetings() {
    int sent = chatAutoGreetingService.processDueGreetings();
    if (sent > 0) {
      log.info("Posted {} auto greeting chat message(s)", sent);
    }
  }
}
