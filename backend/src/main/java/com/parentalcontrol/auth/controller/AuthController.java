package com.parentalcontrol.auth.controller;

import com.parentalcontrol.auth.dto.AuthResponse;
import com.parentalcontrol.auth.dto.DeleteAccountRequest;
import com.parentalcontrol.auth.dto.ForgotPasswordRequest;
import com.parentalcontrol.auth.dto.LoginRequest;
import com.parentalcontrol.auth.dto.RefreshTokenRequest;
import com.parentalcontrol.auth.dto.RegisterRequest;
import com.parentalcontrol.auth.dto.ResetPasswordRequest;
import com.parentalcontrol.auth.dto.VerifyEmailRequest;
import com.parentalcontrol.auth.service.AuthService;
import com.parentalcontrol.auth.service.EmailVerificationService;
import com.parentalcontrol.auth.service.PasswordResetService;
import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.common.constant.ApiConstants;
import com.parentalcontrol.user.service.AccountDeletionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.AUTH)
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Parent registration, login, and token refresh")
public class AuthController {

  private final AuthService authService;
  private final AccountDeletionService accountDeletionService;
  private final PasswordResetService passwordResetService;
  private final EmailVerificationService emailVerificationService;

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Register a new parent account")
  public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
    return ApiResponse.ok("Registration successful", authService.register(request));
  }

  @PostMapping("/login")
  @Operation(summary = "Parent login")
  public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
    return ApiResponse.ok(authService.login(request));
  }

  @PostMapping("/refresh")
  @Operation(summary = "Refresh access token")
  public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
    return ApiResponse.ok(authService.refresh(request));
  }

  @PostMapping("/logout")
  @Operation(summary = "Revoke refresh token and invalidate active sessions")
  public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
    authService.logout(request);
    return ApiResponse.okMessage("Logged out");
  }

  @PostMapping("/forgot-password")
  @Operation(summary = "Request a password reset email")
  public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
    return ApiResponse.okMessage(passwordResetService.requestReset(request));
  }

  @PostMapping("/reset-password")
  @Operation(summary = "Set a new password using a reset token")
  public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
    return ApiResponse.okMessage(passwordResetService.resetPassword(request));
  }

  @PostMapping("/verify-email")
  @Operation(summary = "Verify email address and start trial")
  public ApiResponse<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
    return ApiResponse.okMessage(emailVerificationService.verifyEmail(request.getToken()));
  }

  @PostMapping("/resend-verification")
  @Operation(summary = "Resend email verification link")
  @SecurityRequirement(name = "bearerAuth")
  public ApiResponse<Void> resendVerification() {
    return ApiResponse.okMessage(emailVerificationService.resendVerification());
  }

  @DeleteMapping("/account")
  @Operation(summary = "Delete parent account and purge personal data")
  public ApiResponse<Void> deleteAccount(@Valid @RequestBody DeleteAccountRequest request) {
    accountDeletionService.deleteCurrentAccount(request.getPassword());
    return ApiResponse.okMessage("Account deleted");
  }
}
