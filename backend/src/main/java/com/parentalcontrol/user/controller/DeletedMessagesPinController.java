package com.parentalcontrol.user.controller;

import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.common.constant.ApiConstants;
import com.parentalcontrol.user.dto.DeletedMessagesPinRequest;
import com.parentalcontrol.user.dto.DeletedMessagesPinStatusResponse;
import com.parentalcontrol.user.dto.DeletedMessagesPinVerifyRequest;
import com.parentalcontrol.user.service.DeletedMessagesPinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.PARENT_DELETED_MESSAGES_PIN)
@RequiredArgsConstructor
@Tag(
    name = "Deleted messages PIN",
    description = "PIN required for room owners to review soft-deleted Notes")
@SecurityRequirement(name = "bearerAuth")
public class DeletedMessagesPinController {

  private final DeletedMessagesPinService deletedMessagesPinService;

  @GetMapping("/status")
  @Operation(summary = "Deleted messages PIN configuration status")
  public ApiResponse<DeletedMessagesPinStatusResponse> status() {
    return ApiResponse.ok(deletedMessagesPinService.status());
  }

  @PostMapping
  @Operation(summary = "Create deleted messages PIN (first time)")
  public ApiResponse<Void> create(@Valid @RequestBody DeletedMessagesPinRequest request) {
    deletedMessagesPinService.createPin(request.getPin(), request.getConfirmPin());
    return ApiResponse.okMessage("Deleted messages PIN saved");
  }

  @PutMapping
  @Operation(summary = "Change deleted messages PIN (requires current PIN)")
  public ApiResponse<Void> change(@Valid @RequestBody DeletedMessagesPinRequest request) {
    deletedMessagesPinService.changePin(
        request.getCurrentPin(), request.getPin(), request.getConfirmPin());
    return ApiResponse.okMessage("Deleted messages PIN updated");
  }

  @PostMapping("/verify")
  @Operation(summary = "Verify PIN and unlock deleted message review for this session")
  public ApiResponse<DeletedMessagesPinStatusResponse> verify(
      @Valid @RequestBody DeletedMessagesPinVerifyRequest request) {
    boolean ok = deletedMessagesPinService.verifyPin(request.getPin());
    DeletedMessagesPinStatusResponse status = deletedMessagesPinService.status();
    status.setVerified(ok);
    status.setUnlocked(ok && status.isUnlocked());
    return ApiResponse.ok(status);
  }

  @PostMapping("/lock")
  @Operation(summary = "Lock deleted message review until PIN is entered again")
  public ApiResponse<Void> lock() {
    deletedMessagesPinService.lock();
    return ApiResponse.okMessage("Deleted messages view locked");
  }

  @PostMapping("/reset")
  @Operation(
      summary =
          "Forgot PIN: clear deleted note history in owned rooms, then set a new PIN without the old one")
  public ApiResponse<Void> reset(@Valid @RequestBody DeletedMessagesPinRequest request) {
    deletedMessagesPinService.resetAfterClearingDeletedHistory(
        request.getPin(), request.getConfirmPin());
    return ApiResponse.okMessage("Deleted messages PIN reset after clearing deleted history");
  }
}
