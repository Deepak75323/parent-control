package com.parentalcontrol.user.service;

import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.domain.ParentProfile;
import com.parentalcontrol.user.dto.ReadReceiptStatusResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReadReceiptSettingsService {

  private final ParentProfileDao parentProfileDao;

  @Transactional(readOnly = true)
  public ReadReceiptStatusResponse getStatus() {
    UUID userId = SecurityUtils.requireUserId();
    return ReadReceiptStatusResponse.builder().enabled(isEnabled(userId)).build();
  }

  @Transactional
  public ReadReceiptStatusResponse setEnabled(boolean enabled) {
    UUID userId = SecurityUtils.requireUserId();
    ParentProfile profile =
        parentProfileDao
            .findByUser_Id(userId)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.NOT_FOUND, MessageConstants.USER_NOT_FOUND));
    profile.setReadReceiptsEnabled(enabled);
    parentProfileDao.save(profile);
    return ReadReceiptStatusResponse.builder().enabled(enabled).build();
  }

  @Transactional(readOnly = true)
  public boolean isEnabled(UUID userId) {
    return parentProfileDao
        .findByUser_Id(userId)
        .map(ParentProfile::isReadReceiptsEnabled)
        .orElse(true);
  }
}
