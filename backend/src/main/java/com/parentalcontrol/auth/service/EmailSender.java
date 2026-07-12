package com.parentalcontrol.auth.service;

public interface EmailSender {

  void sendPasswordResetEmail(String toEmail, String resetLink);

  void sendEmailVerificationEmail(String toEmail, String verifyLink);
}
