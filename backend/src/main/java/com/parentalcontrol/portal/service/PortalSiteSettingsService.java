package com.parentalcontrol.portal.service;

import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.AuthenticatedUser;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.portal.PortalAccessRules;
import com.parentalcontrol.portal.domain.PortalSiteSettings;
import com.parentalcontrol.portal.dto.PortalControlResponse;
import com.parentalcontrol.portal.dto.PortalStatusResponse;
import com.parentalcontrol.portal.repository.PortalSiteSettingsRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PortalSiteSettingsService {

  private final PortalSiteSettingsRepository repository;

  @Transactional(readOnly = true)
  public PortalStatusResponse publicStatus() {
    PortalSiteSettings settings = requireSettings();
    return PortalStatusResponse.builder().disabled(settings.isDisabled()).build();
  }

  @Transactional(readOnly = true)
  public PortalControlResponse controlStatus() {
    AuthenticatedUser user = requireAuthenticated();
    PortalSiteSettings settings = requireSettings();
    return toControlResponse(settings, user.getEmail());
  }

  @Transactional
  public PortalControlResponse setDisabled(boolean disabled) {
    AuthenticatedUser user = requireAuthenticated();
    PortalSiteSettings settings = requireSettings();
    if (!PortalAccessRules.canControl(user.getEmail(), settings.isSharedWithEveryone())) {
      throw new BusinessException(ErrorCode.FORBIDDEN, "Not allowed to change portal availability");
    }
    settings.setDisabled(disabled);
    settings.setUpdatedAt(Instant.now());
    settings.setUpdatedBy(user.getId());
    repository.save(settings);
    return toControlResponse(settings, user.getEmail());
  }

  @Transactional
  public PortalControlResponse setSharedWithEveryone(boolean shareWithEveryone) {
    AuthenticatedUser user = requireAuthenticated();
    if (!PortalAccessRules.canShare(user.getEmail())) {
      throw new BusinessException(ErrorCode.FORBIDDEN, "Only the site admin can share this control");
    }
    PortalSiteSettings settings = requireSettings();
    settings.setSharedWithEveryone(shareWithEveryone);
    settings.setUpdatedAt(Instant.now());
    settings.setUpdatedBy(user.getId());
    repository.save(settings);
    return toControlResponse(settings, user.getEmail());
  }

  private PortalControlResponse toControlResponse(PortalSiteSettings settings, String email) {
    return PortalControlResponse.builder()
        .disabled(settings.isDisabled())
        .sharedWithEveryone(settings.isSharedWithEveryone())
        .canControl(PortalAccessRules.canControl(email, settings.isSharedWithEveryone()))
        .canShare(PortalAccessRules.canShare(email))
        .build();
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
