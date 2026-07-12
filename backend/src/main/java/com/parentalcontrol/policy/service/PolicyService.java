package com.parentalcontrol.policy.service;

import com.parentalcontrol.audit.service.AuditService;
import com.parentalcontrol.billing.service.FeatureGateService;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.monitoring.service.DeviceAccessService;
import com.parentalcontrol.monitoring.service.RealtimeEventService;
import com.parentalcontrol.policy.dao.AppBlockRuleDao;
import com.parentalcontrol.policy.dao.DeviceScheduleRuleDao;
import com.parentalcontrol.policy.dao.GeofenceRuleDao;
import com.parentalcontrol.policy.dao.PolicyAssignmentDao;
import com.parentalcontrol.policy.dao.PolicyDao;
import com.parentalcontrol.policy.dao.PolicySyncStateDao;
import com.parentalcontrol.policy.dao.ScreenTimeRuleDao;
import com.parentalcontrol.policy.dao.StreamingPolicyRuleDao;
import com.parentalcontrol.policy.domain.*;
import com.parentalcontrol.policy.dto.*;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.domain.ParentProfile;
import java.time.Instant;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PolicyService {

  private final PolicyDao policyRepository;
  private final AppBlockRuleDao appBlockRuleRepository;
  private final ScreenTimeRuleDao screenTimeRuleRepository;
  private final DeviceScheduleRuleDao deviceScheduleRuleRepository;
  private final GeofenceRuleDao geofenceRuleRepository;
  private final StreamingPolicyRuleDao streamingPolicyRuleRepository;
  private final PolicyAssignmentDao policyAssignmentRepository;
  private final PolicySyncStateDao policySyncStateRepository;
  private final ParentProfileDao parentProfileRepository;
  private final DeviceAccessService deviceAccessService;
  private final PolicyVersionHasher policyVersionHasher;
  private final RealtimeEventService realtimeEventService;
  private final AuditService auditService;
  private final FeatureGateService featureGateService;
  private final TimeExtensionService timeExtensionService;

  @Transactional(readOnly = true)
  public List<PolicyResponse> listPolicies(PolicyType type) {
    ParentProfile parent = requireParent();
    List<Policy> policies =
        type == null
            ? policyRepository.findByParentProfileIdOrderByUpdatedAtDesc(parent.getId())
            : policyRepository.findByParentProfileIdAndPolicyTypeOrderByUpdatedAtDesc(
                parent.getId(), type);
    return policies.stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public PolicyBundleResponse getPoliciesForDevice(UUID deviceId) {
    deviceAccessService.requireOwnedDevice(deviceId);
    return buildBundle(deviceId);
  }

  @Transactional(readOnly = true)
  public PolicyBundleResponse getPoliciesForAuthenticatedDevice() {
    Device device = deviceAccessService.requireAuthenticatedDevice();
    return buildBundle(device.getId());
  }

  @Transactional
  public PolicyResponse createAppBlockPolicy(CreateAppBlockPolicyRequest request) {
    ParentProfile parent = requireParent();
    featureGateService.requireActiveSubscription(parent.getId());
    Policy policy = savePolicy(parent.getId(), request.getName(), PolicyType.APP_BLOCK);
    for (var entry : request.getRules()) {
      appBlockRuleRepository.save(
          AppBlockRule.builder()
              .policyId(policy.getId())
              .packageName(entry.getPackageName())
              .scheduleType(
                  entry.getScheduleType() != null ? entry.getScheduleType() : ScheduleType.ALWAYS)
              .scheduleJson(entry.getScheduleJson() != null ? entry.getScheduleJson() : Map.of())
              .build());
    }
    assignToDevices(policy.getId(), request.getDeviceIds(), parent.getId());
    notifyPolicyUpdate(request.getDeviceIds());
    auditPolicy("POLICY_CREATE_APP_BLOCK", policy.getId(), request.getDeviceIds());
    return toResponse(policy);
  }

  @Transactional
  public PolicyResponse createScreenTimePolicy(CreateScreenTimePolicyRequest request) {
    ParentProfile parent = requireParent();
    featureGateService.requireActiveSubscription(parent.getId());
    Policy policy = savePolicy(parent.getId(), request.getName(), PolicyType.SCREEN_TIME);
    screenTimeRuleRepository.save(
        ScreenTimeRule.builder()
            .policyId(policy.getId())
            .packageName(request.getPackageName())
            .appCategory(request.getAppCategory())
            .dailyLimitMinutes(request.getDailyLimitMinutes())
            .actionOnLimit(request.getActionOnLimit())
            .warnAtPercent(request.getWarnAtPercent())
            .weeklyScheduleJson(
                request.getWeeklyScheduleJson() != null
                    ? request.getWeeklyScheduleJson()
                    : Map.of())
            .build());
    assignToDevices(policy.getId(), request.getDeviceIds(), parent.getId());
    notifyPolicyUpdate(request.getDeviceIds());
    auditPolicy("POLICY_CREATE_SCREEN_TIME", policy.getId(), request.getDeviceIds());
    return toResponse(policy);
  }

  @Transactional
  public PolicyResponse createDeviceSchedulePolicy(CreateDeviceSchedulePolicyRequest request) {
    ParentProfile parent = requireParent();
    featureGateService.requireActiveSubscription(parent.getId());
    Policy policy = savePolicy(parent.getId(), request.getName(), PolicyType.DEVICE_SCHEDULE);
    deviceScheduleRuleRepository.save(
        DeviceScheduleRule.builder()
            .policyId(policy.getId())
            .scheduleType(request.getScheduleType())
            .startTime(LocalTime.parse(request.getStartTime()))
            .endTime(LocalTime.parse(request.getEndTime()))
            .daysOfWeek(request.getDaysOfWeek())
            .lockDevice(request.isLockDevice())
            .build());
    assignToDevices(policy.getId(), request.getDeviceIds(), parent.getId());
    notifyPolicyUpdate(request.getDeviceIds());
    auditPolicy("POLICY_CREATE_DEVICE_SCHEDULE", policy.getId(), request.getDeviceIds());
    return toResponse(policy);
  }

  @Transactional
  public PolicyResponse createGeofencePolicy(CreateGeofencePolicyRequest request) {
    ParentProfile parent = requireParent();
    featureGateService.requireActiveSubscription(parent.getId());
    Policy policy = savePolicy(parent.getId(), request.getName(), PolicyType.GEOFENCE);
    geofenceRuleRepository.save(
        GeofenceRule.builder()
            .policyId(policy.getId())
            .name(request.getGeofenceName())
            .latitude(request.getLatitude())
            .longitude(request.getLongitude())
            .radiusM(request.getRadiusM())
            .alertOnEnter(request.isAlertOnEnter())
            .alertOnExit(request.isAlertOnExit())
            .build());
    assignToDevices(policy.getId(), request.getDeviceIds(), parent.getId());
    notifyPolicyUpdate(request.getDeviceIds());
    auditPolicy("POLICY_CREATE_GEOFENCE", policy.getId(), request.getDeviceIds());
    return toResponse(policy);
  }

  @Transactional
  public PolicyResponse createStreamingPolicy(CreateStreamingPolicyRequest request) {
    ParentProfile parent = requireParent();
    featureGateService.requireActiveSubscription(parent.getId());
    Policy policy = savePolicy(parent.getId(), request.getName(), PolicyType.STREAMING);
    streamingPolicyRuleRepository.save(
        StreamingPolicyRule.builder()
            .policyId(policy.getId())
            .screenShareAllowed(request.isScreenShareAllowed())
            .cameraAllowed(request.isCameraAllowed())
            .audioAllowed(request.isAudioAllowed())
            .build());
    assignToDevices(policy.getId(), request.getDeviceIds(), parent.getId());
    notifyPolicyUpdate(request.getDeviceIds());
    auditPolicy("POLICY_CREATE_STREAMING", policy.getId(), request.getDeviceIds());
    return toResponse(policy);
  }

  @Transactional
  public void deletePolicy(UUID policyId) {
    ParentProfile parent = requireParent();
    Policy policy =
        policyRepository
            .findById(policyId)
            .orElseThrow(
                () ->
                    new BusinessException(ErrorCode.NOT_FOUND, MessageConstants.POLICY_NOT_FOUND));
    if (!policy.getParentProfileId().equals(parent.getId())) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    List<UUID> deviceIds =
        policyAssignmentRepository.findByPolicyId(policyId).stream()
            .map(PolicyAssignment::getDeviceId)
            .toList();
    policyRepository.delete(policy);
    notifyPolicyUpdate(deviceIds);
    auditPolicy("POLICY_DELETE", policyId, deviceIds);
  }

  private void auditPolicy(String action, UUID policyId, List<UUID> deviceIds) {
    UUID deviceId = deviceIds != null && !deviceIds.isEmpty() ? deviceIds.get(0) : null;
    auditService.log(
        action, "policy:" + policyId, deviceId, Map.of("policyId", policyId.toString()));
  }

  @Transactional
  public void acknowledgeSync(PolicyAckRequest request) {
    Device device = deviceAccessService.requireAuthenticatedDevice();
    PolicySyncState state =
        policySyncStateRepository
            .findByDeviceId(device.getId())
            .orElse(
                PolicySyncState.builder()
                    .deviceId(device.getId())
                    .policyVersion(request.getPolicyVersion())
                    .lastSyncAt(Instant.now())
                    .build());
    state.setPolicyVersion(request.getPolicyVersion());
    state.setLastAckAt(Instant.now());
    state.setLastSyncAt(Instant.now());
    policySyncStateRepository.save(state);
  }

  @Transactional(readOnly = true)
  public List<GeofenceSummaryResponse> getGeofencesForDevice(UUID deviceId) {
    Device device = deviceAccessService.requireOwnedDevice(deviceId);
    featureGateService.requireActiveSubscription(device.getParentProfileId());
    PolicyBundleResponse bundle = buildBundle(deviceId);
    return bundle.getGeofences().stream()
        .map(
            g ->
                GeofenceSummaryResponse.builder()
                    .policyId(g.getPolicyId().toString())
                    .ruleId(g.getRuleId().toString())
                    .name(g.getName())
                    .latitude(g.getLatitude())
                    .longitude(g.getLongitude())
                    .radiusM(g.getRadiusM())
                    .alertOnEnter(g.isAlertOnEnter())
                    .alertOnExit(g.isAlertOnExit())
                    .build())
        .toList();
  }

  private PolicyBundleResponse buildBundle(UUID deviceId) {
    List<UUID> policyIds = policyAssignmentRepository.findActivePolicyIdsByDeviceId(deviceId);
    if (policyIds.isEmpty()) {
      PolicyBundleResponse empty =
          PolicyBundleResponse.builder()
              .deviceId(deviceId)
              .appBlocks(List.of())
              .screenTime(List.of())
              .deviceSchedules(List.of())
              .geofences(List.of())
              .streaming(
                  StreamingPolicyDto.builder()
                      .screenShareAllowed(true)
                      .cameraAllowed(true)
                      .audioAllowed(true)
                      .enabled(true)
                      .build())
              .timeGrants(timeExtensionService.activeGrantsForDevice(deviceId))
              .build();
      empty.setPolicyVersion(policyVersionHasher.hash(empty));
      return empty;
    }

    List<Policy> policies =
        policyRepository.findAllById(policyIds).stream().filter(Policy::getEnabled).toList();

    List<AppBlockPolicyDto> appBlocks = new ArrayList<>();
    List<ScreenTimePolicyDto> screenTime = new ArrayList<>();
    List<DeviceSchedulePolicyDto> deviceSchedules = new ArrayList<>();
    List<GeofencePolicyDto> geofences = new ArrayList<>();
    StreamingPolicyDto streaming =
        StreamingPolicyDto.builder()
            .screenShareAllowed(true)
            .cameraAllowed(true)
            .audioAllowed(true)
            .enabled(true)
            .build();

    for (Policy policy : policies) {
      switch (policy.getPolicyType()) {
        case APP_BLOCK ->
            appBlockRuleRepository
                .findByPolicyId(policy.getId())
                .forEach(
                    rule ->
                        appBlocks.add(
                            AppBlockPolicyDto.builder()
                                .policyId(policy.getId())
                                .ruleId(rule.getId())
                                .packageName(rule.getPackageName())
                                .scheduleType(rule.getScheduleType())
                                .scheduleJson(rule.getScheduleJson())
                                .enabled(policy.getEnabled())
                                .build()));
        case SCREEN_TIME ->
            screenTimeRuleRepository
                .findByPolicyId(policy.getId())
                .forEach(
                    rule ->
                        screenTime.add(
                            ScreenTimePolicyDto.builder()
                                .policyId(policy.getId())
                                .ruleId(rule.getId())
                                .packageName(rule.getPackageName())
                                .appCategory(rule.getAppCategory())
                                .dailyLimitMinutes(rule.getDailyLimitMinutes())
                                .actionOnLimit(rule.getActionOnLimit())
                                .warnAtPercent(rule.getWarnAtPercent())
                                .weeklyScheduleJson(rule.getWeeklyScheduleJson())
                                .enabled(policy.getEnabled())
                                .build()));
        case DEVICE_SCHEDULE ->
            deviceScheduleRuleRepository
                .findByPolicyId(policy.getId())
                .forEach(
                    rule ->
                        deviceSchedules.add(
                            DeviceSchedulePolicyDto.builder()
                                .policyId(policy.getId())
                                .ruleId(rule.getId())
                                .scheduleType(rule.getScheduleType())
                                .startTime(rule.getStartTime().toString())
                                .endTime(rule.getEndTime().toString())
                                .daysOfWeek(rule.getDaysOfWeek())
                                .lockDevice(rule.getLockDevice())
                                .enabled(policy.getEnabled())
                                .build()));
        case GEOFENCE ->
            geofenceRuleRepository
                .findByPolicyId(policy.getId())
                .forEach(
                    rule ->
                        geofences.add(
                            GeofencePolicyDto.builder()
                                .policyId(policy.getId())
                                .ruleId(rule.getId())
                                .name(rule.getName())
                                .latitude(rule.getLatitude())
                                .longitude(rule.getLongitude())
                                .radiusM(rule.getRadiusM())
                                .alertOnEnter(rule.getAlertOnEnter())
                                .alertOnExit(rule.getAlertOnExit())
                                .enabled(policy.getEnabled())
                                .build()));
        case STREAMING -> {
          var streamRule = streamingPolicyRuleRepository.findByPolicyId(policy.getId());
          if (streamRule.isPresent()) {
            var rule = streamRule.get();
            streaming =
                StreamingPolicyDto.builder()
                    .policyId(policy.getId())
                    .screenShareAllowed(rule.getScreenShareAllowed())
                    .cameraAllowed(rule.getCameraAllowed())
                    .audioAllowed(rule.getAudioAllowed())
                    .enabled(policy.getEnabled())
                    .build();
          }
        }
        default -> {}
      }
    }

    PolicyBundleResponse bundle =
        PolicyBundleResponse.builder()
            .deviceId(deviceId)
            .appBlocks(appBlocks)
            .screenTime(screenTime)
            .deviceSchedules(deviceSchedules)
            .geofences(geofences)
            .streaming(streaming)
            .timeGrants(timeExtensionService.activeGrantsForDevice(deviceId))
            .build();
    String version = policyVersionHasher.hash(bundle);
    bundle.setPolicyVersion(version);

    PolicySyncState syncState =
        policySyncStateRepository
            .findByDeviceId(deviceId)
            .orElse(PolicySyncState.builder().deviceId(deviceId).build());
    syncState.setPolicyVersion(version);
    syncState.setLastSyncAt(Instant.now());
    policySyncStateRepository.save(syncState);

    return bundle;
  }

  private Policy savePolicy(UUID parentProfileId, String name, PolicyType type) {
    return policyRepository.save(
        Policy.builder()
            .parentProfileId(parentProfileId)
            .name(name)
            .policyType(type)
            .enabled(true)
            .version(1)
            .build());
  }

  private void assignToDevices(UUID policyId, List<UUID> deviceIds, UUID parentProfileId) {
    for (UUID deviceId : deviceIds) {
      Device device = deviceAccessService.requireOwnedDevice(deviceId);
      if (!device.getParentProfileId().equals(parentProfileId)) {
        throw new BusinessException(ErrorCode.FORBIDDEN);
      }
      boolean exists =
          policyAssignmentRepository.findByPolicyId(policyId).stream()
              .anyMatch(a -> a.getDeviceId().equals(deviceId));
      if (!exists) {
        policyAssignmentRepository.save(
            PolicyAssignment.builder().policyId(policyId).deviceId(deviceId).active(true).build());
      }
    }
  }

  private void notifyPolicyUpdate(List<UUID> deviceIds) {
    UUID parentUserId = SecurityUtils.requireUserId();
    for (UUID deviceId : deviceIds) {
      PolicyBundleResponse bundle = buildBundle(deviceId);
      realtimeEventService.publishPolicyUpdated(parentUserId, deviceId, bundle.getPolicyVersion());
      realtimeEventService.publishDevicePolicyUpdate(deviceId, bundle.getPolicyVersion());
    }
  }

  private ParentProfile requireParent() {
    UUID userId = SecurityUtils.requireUserId();
    return parentProfileRepository
        .findByUser_Id(userId)
        .orElseThrow(
            () ->
                new BusinessException(
                    ErrorCode.NOT_FOUND, MessageConstants.PARENT_PROFILE_NOT_FOUND));
  }

  private PolicyResponse toResponse(Policy policy) {
    List<UUID> deviceIds =
        policyAssignmentRepository.findByPolicyId(policy.getId()).stream()
            .map(PolicyAssignment::getDeviceId)
            .collect(Collectors.toList());
    return PolicyResponse.builder()
        .id(policy.getId())
        .name(policy.getName())
        .policyType(policy.getPolicyType())
        .enabled(policy.getEnabled())
        .version(policy.getVersion())
        .deviceIds(deviceIds)
        .updatedAt(policy.getUpdatedAt())
        .build();
  }
}
