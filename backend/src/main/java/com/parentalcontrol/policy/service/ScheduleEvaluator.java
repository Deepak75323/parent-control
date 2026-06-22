package com.parentalcontrol.policy.service;

import com.parentalcontrol.policy.domain.ScheduleType;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Component
public class ScheduleEvaluator {

    public boolean isAppBlockActive(ScheduleType scheduleType, Map<String, Object> scheduleJson, ZoneId zone) {
        if (scheduleType == null || scheduleType == ScheduleType.ALWAYS) {
            return true;
        }
        LocalTime now = LocalTime.now(zone);
        if (scheduleType == ScheduleType.TIME_BASED) {
            LocalTime start = parseTime(scheduleJson.get("startTime"));
            LocalTime end = parseTime(scheduleJson.get("endTime"));
            if (start == null || end == null) {
                return true;
            }
            return isWithinTimeRange(now, start, end);
        }
        if (scheduleType == ScheduleType.RECURRING) {
            String today = DayOfWeek.from(LocalDate.now(zone)).name().substring(0, 3);
            @SuppressWarnings("unchecked")
            List<String> days = scheduleJson.get("daysOfWeek") instanceof List<?> list
                    ? (List<String>) list
                    : List.of();
            if (!days.isEmpty() && days.stream().noneMatch(d -> d.equalsIgnoreCase(today))) {
                return false;
            }
            LocalTime start = parseTime(scheduleJson.get("startTime"));
            LocalTime end = parseTime(scheduleJson.get("endTime"));
            if (start == null || end == null) {
                return true;
            }
            return isWithinTimeRange(now, start, end);
        }
        return true;
    }

    public boolean isDeviceScheduleActive(
            List<String> daysOfWeek,
            String startTime,
            String endTime,
            ZoneId zone) {
        LocalTime now = LocalTime.now(zone);
        String today = DayOfWeek.from(LocalDate.now(zone)).name().substring(0, 3);
        if (daysOfWeek != null && !daysOfWeek.isEmpty()
                && daysOfWeek.stream().noneMatch(d -> d.equalsIgnoreCase(today))) {
            return false;
        }
        LocalTime start = LocalTime.parse(startTime);
        LocalTime end = LocalTime.parse(endTime);
        return isWithinTimeRange(now, start, end);
    }

    private boolean isWithinTimeRange(LocalTime now, LocalTime start, LocalTime end) {
        if (start.isBefore(end) || start.equals(end)) {
            return !now.isBefore(start) && !now.isAfter(end);
        }
        return !now.isBefore(start) || !now.isAfter(end);
    }

    private LocalTime parseTime(Object value) {
        if (value == null) {
            return null;
        }
        return LocalTime.parse(value.toString());
    }
}
