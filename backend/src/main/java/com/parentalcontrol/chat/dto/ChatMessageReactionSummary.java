package com.parentalcontrol.chat.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChatMessageReactionSummary {
  String emoji;
  int count;
  boolean reactedByMe;
  List<String> reactorLabels;
}
