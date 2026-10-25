package com.parentalcontrol.portal.service;

import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.AuthenticatedUser;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.portal.PortalAccessRules;
import com.parentalcontrol.portal.domain.PortalOperator;
import com.parentalcontrol.portal.domain.PortalSiteSettings;
import com.parentalcontrol.portal.dto.PortalControlResponse;
import com.parentalcontrol.portal.dto.PortalOperatorDto;
import com.parentalcontrol.portal.dto.PortalStatusResponse;
import com.parentalcontrol.portal.repository.PortalOperatorRepository;
import com.parentalcontrol.portal.repository.PortalSiteSettingsRepository;
import com.parentalcontrol.user.dao.UserDao;
import com.parentalcontrol.user.domain.ParentProfile;
import com.parentalcontrol.user.domain.User;
import com.parentalcontrol.user.service.FamilyHandleService;
import com.parentalcontrol.user.util.FamilyHandleNormalizer;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PortalSiteSettingsService {

  private final PortalSiteSettingsRepository repository;
  private final PortalOperatorRepository operatorRepository;
  private final FamilyHandleService familyHandleService;
  private final UserDao userRepository;
  private final PasswordEncoder passwordEncoder;

  @Transactional(readOnly = true)
  public PortalStatusResponse publicStatus() {
    PortalSiteSettings settings = requireSettings();
    return PortalStatusResponse.builder().disabled(settings.isDisabled()).build();
  }

  @Transactional(readOnly = true)
  public PortalControlResponse controlStatus() {
    AuthenticatedUser user = requireAuthenticated();
    return toControlResponse(requireSettings(), user);
  }

  @Transactional
  public PortalControlResponse setDisabled(boolean disabled, String password) {
    AuthenticatedUser user = requireAuthenticated();
    requirePassword(user, password);
    PortalSiteSettings settings = requireSettings();
    if (!PortalAccessRules.canControl(user.getEmail(), user.getId(), operatorUserIds())) {
      throw new BusinessException(ErrorCode.FORBIDDEN, "Not allowed to change portal availability");
    }
    settings.setDisabled(disabled);
    settings.setUpdatedAt(Instant.now());
    settings.setUpdatedBy(user.getId());
    repository.save(settings);
    return toControlResponse(settings, user);
  }

  @Transactional
  public PortalControlResponse grantByHandle(String rawHandle, String password) {
    AuthenticatedUser user = requireAuthenticated();
    requirePassword(user, password);
    if (!PortalAccessRules.canShare(user.getEmail())) {
      throw new BusinessException(
          ErrorCode.FORBIDDEN, "Only the site admin can share this control by handle");
    }

    String normalized = FamilyHandleNormalizer.normalize(rawHandle);
    if (normalized == null || !FamilyHandleNormalizer.isValidFormat(normalized)) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR, MessageConstants.FAMILY_HANDLE_INVALID);
    }

    ParentProfile target =
        familyHandleService
            .findByHandle(normalized)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.NOT_FOUND, "No parent found with that family handle"));

    UUID targetUserId = target.getUserId();
    if (targetUserId == null) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "No parent found with that family handle");
    }
    if (PortalAccessRules.isBootstrapAdmin(
        target.getUser() != null ? target.getUser().getEmail() : null)) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, "Site admin already has this control");
    }

    if (!operatorRepository.existsByUserId(targetUserId)) {
      operatorRepository.save(
          PortalOperator.builder()
              .userId(targetUserId)
              .familyHandle(normalized)
              .grantedAt(Instant.now())
              .grantedBy(user.getId())
              .build());
    }

    PortalSiteSettings settings = requireSettings();
    settings.setUpdatedAt(Instant.now());
    settings.setUpdatedBy(user.getId());
    repository.save(settings);
    return toControlResponse(settings, user);
  }

  @Transactional
  public PortalControlResponse revokeOperator(UUID operatorUserId, String password) {
    AuthenticatedUser user = requireAuthenticated();
    requirePassword(user, password);
    if (!PortalAccessRules.canShare(user.getEmail())) {
      throw new BusinessException(
          ErrorCode.FORBIDDEN, "Only the site admin can revoke this control");
    }
    if (operatorUserId == null) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Operator user id is required");
    }
    operatorRepository.deleteById(operatorUserId);

    PortalSiteSettings settings = requireSettings();
    settings.setUpdatedAt(Instant.now());
    settings.setUpdatedBy(user.getId());
    repository.save(settings);
    return toControlResponse(settings, user);
  }

  private PortalControlResponse toControlResponse(
      PortalSiteSettings settings, AuthenticatedUser user) {
    List<PortalOperator> operators = operatorRepository.findAllByOrderByGrantedAtAsc();
    Set<UUID> operatorIds =
        operators.stream().map(PortalOperator::getUserId).collect(Collectors.toSet());
    List<PortalOperatorDto> operatorDtos =
        operators.stream()
            .map(
                op ->
                    PortalOperatorDto.builder()
                        .userId(op.getUserId())
                        .familyHandle(op.getFamilyHandle())
                        .build())
            .toList();
    return PortalControlResponse.builder()
        .disabled(settings.isDisabled())
        .canControl(PortalAccessRules.canControl(user.getEmail(), user.getId(), operatorIds))
        .canShare(PortalAccessRules.canShare(user.getEmail()))
        .operators(operatorDtos)
        .build();
  }

  private Set<UUID> operatorUserIds() {
    return operatorRepository.findAllByOrderByGrantedAtAsc().stream()
        .map(PortalOperator::getUserId)
        .collect(Collectors.toSet());
  }

  private void requirePassword(AuthenticatedUser principal, String password) {
    if (password == null || password.isBlank()) {
      throw new BusinessException(
          ErrorCode.INVALID_CREDENTIALS, MessageConstants.INCORRECT_PASSWORD);
    }
    User user =
        userRepository
            .findById(principal.getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
      throw new BusinessException(
          ErrorCode.INVALID_CREDENTIALS, MessageConstants.INCORRECT_PASSWORD);
    }
  }

  private PortalSiteSettings requireSettings() {
    return repository
        .findById(PortalSiteSettings.SINGLETON_ID)
        .orElseGet(
            () ->
                repository.save(
                    PortalSiteSettings.builder()
                        .id(PortalSiteSettings.SINGLETON_ID)
                        .disabled(false)
                        .sharedWithEveryone(false)
                        .updatedAt(Instant.now())
                        .build()));
  }

  private AuthenticatedUser requireAuthenticated() {
    return SecurityUtils.currentUser()
        .orElseThrow(() -> new IllegalStateException("No authenticated user"));
  }
}
