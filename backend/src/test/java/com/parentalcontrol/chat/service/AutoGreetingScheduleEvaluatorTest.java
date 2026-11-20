package com.parentalcontrol.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class AutoGreetingScheduleEvaluatorTest {

  @Test
  void morningWindowSevenOhFiveToNine() {
    LocalTime start = LocalTime.of(7, 5);
    LocalTime end = LocalTime.of(9, 0);
    assertFalse(AutoGreetingScheduleEvaluator.isInMorningWindow(LocalTime.of(7, 4), start, end));
    assertTrue(AutoGreetingScheduleEvaluator.isInMorningWindow(LocalTime.of(7, 5), start, end));
    assertTrue(AutoGreetingScheduleEvaluator.isInMorningWindow(LocalTime.of(8, 30), start, end));
    assertFalse(AutoGreetingScheduleEvaluator.isInMorningWindow(LocalTime.of(9, 0), start, end));
    assertFalse(AutoGreetingScheduleEvaluator.isInMorningWindow(LocalTime.NOON, start, end));
  }

  @Test
  void morningWindowDefaultsStillCoverLateTickBeforeEnd() {
    LocalTime start = LocalTime.of(7, 5);
    LocalTime end = LocalTime.NOON;
    assertTrue(AutoGreetingScheduleEvaluator.isInMorningWindow(LocalTime.of(9, 0), start, end));
    assertFalse(AutoGreetingScheduleEvaluator.isInMorningWindow(LocalTime.NOON, start, end));
  }

  @Test
  void eveningWindowTwentyThreeFiftyFiveToMidnight() {
    LocalTime start = LocalTime.of(23, 55);
    LocalTime end = LocalTime.MIDNIGHT;
    assertFalse(AutoGreetingScheduleEvaluator.isInEveningWindow(LocalTime.of(23, 54), start, end));
    assertTrue(AutoGreetingScheduleEvaluator.isInEveningWindow(LocalTime.of(23, 55), start, end));
    assertTrue(AutoGreetingScheduleEvaluator.isInEveningWindow(LocalTime.of(23, 59), start, end));
    assertFalse(AutoGreetingScheduleEvaluator.isInEveningWindow(LocalTime.of(0, 0), start, end));
    assertFalse(AutoGreetingScheduleEvaluator.isInEveningWindow(LocalTime.of(0, 1), start, end));
  }

  @Test
  void eveningAnchorForMidnightEndIsSameDay() {
    LocalDate day = LocalDate.of(2026, 11, 20);
    assertEquals(
        day,
        AutoGreetingScheduleEvaluator.eveningAnchorDate(
            day, LocalTime.of(23, 56), LocalTime.of(23, 55), LocalTime.MIDNIGHT));
  }
}
