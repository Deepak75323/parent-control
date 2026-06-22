package com.parentalcontrol.monitoring.service;

import com.parentalcontrol.common.api.PageResponse;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.repository.DeviceRepository;
import com.parentalcontrol.monitoring.domain.AppUsageLog;
import com.parentalcontrol.monitoring.domain.DeviceInstalledApp;
import com.parentalcontrol.monitoring.domain.LocationHistory;
import com.parentalcontrol.monitoring.domain.NotificationLog;
import com.parentalcontrol.monitoring.dto.IngestInstalledAppsRequest;
import com.parentalcontrol.monitoring.dto.IngestNotificationsRequest;
import com.parentalcontrol.monitoring.dto.IngestResultResponse;
import com.parentalcontrol.monitoring.dto.IngestUsageStatsRequest;
import com.parentalcontrol.monitoring.dto.InstalledAppResponse;
import com.parentalcontrol.monitoring.dto.LocationHistoryResponse;
import com.parentalcontrol.monitoring.dto.LocationIngestRequest;
import com.parentalcontrol.monitoring.dto.NotificationResponse;
import com.parentalcontrol.monitoring.dto.UsageStatsResponse;
import com.parentalcontrol.monitoring.repository.AppUsageLogRepository;
import com.parentalcontrol.monitoring.repository.DeviceInstalledAppRepository;
import com.parentalcontrol.monitoring.repository.LocationHistoryRepository;
import com.parentalcontrol.monitoring.kafka.MonitoringKafkaPublisher;
import com.parentalcontrol.monitoring.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MonitoringService {

    private final NotificationLogRepository notificationLogRepository;
    private final LocationHistoryRepository locationHistoryRepository;
    private final AppUsageLogRepository appUsageLogRepository;
    private final DeviceInstalledAppRepository deviceInstalledAppRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceAccessService deviceAccessService;
    private final RealtimeEventService realtimeEventService;
    private final MonitoringKafkaPublisher monitoringKafkaPublisher;

    @Transactional
    public IngestResultResponse ingestNotifications(IngestNotificationsRequest request) {
        Device device = deviceAccessService.requireAuthenticatedDevice();
        List<NotificationLog> logs = new ArrayList<>();

        request.getNotifications().forEach(item -> logs.add(NotificationLog.builder()
                .deviceId(device.getId())
                .packageName(item.getPackageName())
                .title(item.getTitle())
                .body(item.getBody())
                .postedAt(item.getPostedAt())
                .build()));

        List<NotificationLog> saved = notificationLogRepository.saveAll(logs);
        saved.forEach(log -> realtimeEventService.publishNotification(device, log));
        monitoringKafkaPublisher.notifications(device.getId(), saved.size());

        return IngestResultResponse.builder().accepted(saved.size()).build();
    }

    @Transactional
    public LocationHistoryResponse ingestLocation(LocationIngestRequest request) {
        Device device = deviceAccessService.requireAuthenticatedDevice();
        Instant recordedAt = request.getRecordedAt() != null ? request.getRecordedAt() : Instant.now();

        LocationHistory entry = LocationHistory.builder()
                .deviceId(device.getId())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .accuracyM(request.getAccuracyM())
                .recordedAt(recordedAt)
                .build();
        entry = locationHistoryRepository.save(entry);

        device.setLastLatitude(request.getLatitude());
        device.setLastLongitude(request.getLongitude());
        device = deviceRepository.save(device);

        realtimeEventService.publishLocation(device, entry);
        realtimeEventService.publishDeviceStatus(device);
        monitoringKafkaPublisher.location(device.getId(), request.getLatitude(), request.getLongitude());

        return toLocationResponse(entry);
    }

    @Transactional
    public IngestResultResponse ingestUsageStats(IngestUsageStatsRequest request) {
        Device device = deviceAccessService.requireAuthenticatedDevice();
        List<AppUsageLog> logs = new ArrayList<>();

        request.getEntries().forEach(item -> logs.add(AppUsageLog.builder()
                .deviceId(device.getId())
                .packageName(item.getPackageName())
                .usageMs(item.getUsageMs())
                .recordedAt(item.getRecordedAt())
                .build()));

        List<AppUsageLog> saved = appUsageLogRepository.saveAll(logs);
        saved.forEach(log -> realtimeEventService.publishUsageStats(device, log));
        monitoringKafkaPublisher.usageStats(device.getId(), saved.size());

        return IngestResultResponse.builder().accepted(saved.size()).build();
    }

    @Transactional
    public IngestResultResponse ingestInstalledApps(IngestInstalledAppsRequest request) {
        Device device = deviceAccessService.requireAuthenticatedDevice();
        Instant syncedAt = Instant.now();
        deviceInstalledAppRepository.softDeleteAllByDeviceId(device.getId());

        List<DeviceInstalledApp> apps = request.getApps().stream()
                .map(item -> DeviceInstalledApp.builder()
                        .deviceId(device.getId())
                        .packageName(item.getPackageName())
                        .appLabel(item.getAppLabel())
                        .appCategory(item.getAppCategory())
                        .syncedAt(syncedAt)
                        .build())
                .toList();
        deviceInstalledAppRepository.saveAll(apps);

        return IngestResultResponse.builder().accepted(apps.size()).build();
    }

    @Transactional(readOnly = true)
    public List<InstalledAppResponse> getInstalledApps(UUID deviceId) {
        deviceAccessService.requireOwnedDevice(deviceId);
        return deviceInstalledAppRepository.findByDeviceIdOrderByAppLabelAsc(deviceId).stream()
                .map(app -> InstalledAppResponse.builder()
                        .id(app.getId())
                        .packageName(app.getPackageName())
                        .appLabel(app.getAppLabel())
                        .appCategory(app.getAppCategory())
                        .syncedAt(app.getSyncedAt())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getNotifications(UUID deviceId, int page, int size) {
        deviceAccessService.requireOwnedDevice(deviceId);
        Page<NotificationLog> result = notificationLogRepository.findByDeviceIdOrderByPostedAtDesc(
                deviceId,
                PageRequest.of(page, Math.min(size, 100))
        );
        return PageResponse.of(
                result.getContent().stream().map(this::toNotificationResponse).toList(),
                page,
                result.getSize(),
                result.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<LocationHistoryResponse> getLocationHistory(UUID deviceId, int page, int size) {
        deviceAccessService.requireOwnedDevice(deviceId);
        Page<LocationHistory> result = locationHistoryRepository.findByDeviceIdOrderByRecordedAtDesc(
                deviceId,
                PageRequest.of(page, Math.min(size, 100))
        );
        return PageResponse.of(
                result.getContent().stream().map(this::toLocationResponse).toList(),
                page,
                result.getSize(),
                result.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<UsageStatsResponse> getUsageStats(UUID deviceId, int page, int size) {
        deviceAccessService.requireOwnedDevice(deviceId);
        Page<AppUsageLog> result = appUsageLogRepository.findByDeviceIdOrderByRecordedAtDesc(
                deviceId,
                PageRequest.of(page, Math.min(size, 100))
        );
        return PageResponse.of(
                result.getContent().stream().map(this::toUsageStatsResponse).toList(),
                page,
                result.getSize(),
                result.getTotalElements()
        );
    }

    private NotificationResponse toNotificationResponse(NotificationLog log) {
        return NotificationResponse.builder()
                .id(log.getId())
                .packageName(log.getPackageName())
                .title(log.getTitle())
                .body(log.getBody())
                .postedAt(log.getPostedAt())
                .build();
    }

    private LocationHistoryResponse toLocationResponse(LocationHistory entry) {
        return LocationHistoryResponse.builder()
                .id(entry.getId())
                .latitude(entry.getLatitude())
                .longitude(entry.getLongitude())
                .accuracyM(entry.getAccuracyM())
                .recordedAt(entry.getRecordedAt())
                .build();
    }

    private UsageStatsResponse toUsageStatsResponse(AppUsageLog log) {
        return UsageStatsResponse.builder()
                .id(log.getId())
                .deviceId(log.getDeviceId())
                .packageName(log.getPackageName())
                .usageMs(log.getUsageMs())
                .recordedAt(log.getRecordedAt())
                .build();
    }
}
