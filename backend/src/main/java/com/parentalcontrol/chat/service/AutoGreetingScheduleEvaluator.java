package com.parentalcontrol.chat.service;

import java.time.LocalDate;
import java.time.LocalTime;

/** Pure schedule rules for settings-gated auto chat greetings. */
public final class AutoGreetingScheduleEvaluator {

  private AutoGreetingScheduleEvaluator() {}

  /**
   * Morning window {@code [start, end)}. Same non-wrapping rules as evening when end is not
   * midnight.
   */
  public static boolean isInMorningWindow(LocalTime now, LocalTime start, LocalTime end) {
    return isInWindow(now, start, end);
  }

  /**
   * Evening window. When {@code end} is midnight ({@code 00:00}), the window is {@code [start,
   * end-of-day]}. When {@code start} is after {@code end}, the window wraps midnight.
   */
  public static boolean isInEveningWindow(LocalTime now, LocalTime start, LocalTime end) {
    return isInWindow(now, start, end);
  }

  private static boolean isInWindow(LocalTime now, LocalTime start, LocalTime end) {
    if (now == null || start == null || end == null) {
      return false;
    }
    if (end.equals(LocalTime.MIDNIGHT)) {
      return !now.isBefore(start);
    }
    if (start.equals(end)) {
      return false;
    }
    if (start.isBefore(end)) {
      return !now.isBefore(start) && now.isBefore(end);
    }
    return !now.isBefore(start) || now.isBefore(end);
  }

  /**
   * Calendar day key for an evening send. For a wrap-around window, times after midnight before
   * {@code end} belong to the previous evening.
   */
  public static LocalDate eveningAnchorDate(LocalDate localDate, LocalTime now, LocalTime start, LocalTime end) {
    if (localDate == null || now == null || start == null || end == null) {
      return localDate;
    }
    if (end.equals(LocalTime.MIDNIGHT)) {
      return localDate;
    }
    if (start.isAfter(end) && now.isBefore(end)) {
      return localDate.minusDays(1);
    }
    return localDate;
  }
}
