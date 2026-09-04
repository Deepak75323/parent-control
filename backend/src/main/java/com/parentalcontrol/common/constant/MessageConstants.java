package com.parentalcontrol.common.constant;

/**
 * Central catalogue of user-facing / API error and status messages. Prefer these constants over
 * string literals in services and security code.
 */
public final class MessageConstants {

  public static final String ACCOUNT_TEMPORARILY_LOCKED_TOO_MANY_FAILED_ATTEMPTS =
      "Account temporarily locked — too many failed attempts. ";

  public static final String ACTIVE_SUBSCRIPTION_REQUIRED_TO_ADD_DEVICES =
      "Active subscription required to add devices";

  public static final String ACTIVE_SUBSCRIPTION_REQUIRED = "Active subscription required.";

  public static final String
      ACTIVE_SUBSCRIPTION_REQUIRED_VERIFY_YOUR_EMAIL_AND_START_A_TRIAL_OR_UPGRADE_YOUR =
          "Active subscription required. Verify your email and start a trial, or upgrade your plan.";

  public static final String APP_NOT_PLAY_RECOGNIZED = "App not Play-recognized";

  public static final String AUDIO_STREAMING_DISABLED_BY_POLICY =
      "Audio streaming disabled by policy";

  public static final String AUTHENTICATION_SERVICE_TEMPORARILY_UNAVAILABLE_TRY_AGAIN_LATER =
      "Authentication service temporarily unavailable — try again later";

  public static final String CAMERA_STREAMING_DISABLED_BY_POLICY =
      "Camera streaming disabled by policy";

  public static final String CAMERA_SWITCH_ONLY_APPLIES_TO_CAMERA_STREAMS =
      "Camera switch only applies to CAMERA streams";

  public static final String CANNOT_CHECKOUT_FREE_PLAN = "Cannot checkout free plan";

  public static final String CHILD_LOGIN_PASSWORD_IS_REQUIRED = "Child login password is required";

  public static final String CHILD_LOGIN_PASSWORD_MUST_BE_AT_LEAST_4_CHARACTERS =
      "Child login password must be at least 4 characters";

  public static final String COMMAND_NOT_FOUND = "Command not found";

  public static final String FAMILY_HANDLE_ALREADY_TAKEN =
      "This family handle is already taken. Choose another.";

  public static final String FAMILY_HANDLE_INVALID =
      "Family handle must be 3–20 characters: letters, numbers, dots, underscores, or hyphens.";

  public static final String FAMILY_HANDLE_IS_REQUIRED =
      "Family handle is required. Enter the handle from the parent app.";

  public static final String FAMILY_HANDLE_RESERVED =
      "This family handle is reserved. Choose another.";

  public static final String SET_YOUR_FAMILY_HANDLE_IN_SETTINGS_BEFORE_GENERATING_A_PAIRING_CODE =
      "Set your family handle in Settings before generating a pairing code";

  public static final String CONCURRENT_REFRESH_ALREADY_IN_PROGRESS_PLEASE_RETRY =
      "Concurrent refresh already in progress — please retry";

  public static final String CONSENT_MUST_BE_ACCEPTED = "Consent must be accepted";

  public static final String CONSENT_MUST_BE_ACCEPTED_ON_CHILD_DEVICE =
      "Consent must be accepted on child device";

  public static final String COULD_NOT_CREATE_PAIRING_CODE = "Could not create pairing code";

  public static final String COULD_NOT_GENERATE_UNIQUE_PAIRING_CODE =
      "Could not generate unique pairing code";

  public static final String DEVICE_HMAC_NOT_CONFIGURED_RE_PAIR_DEVICE =
      "Device HMAC not configured — re-pair device";

  public static final String DEVICE_FAILED_TRUST_REQUIREMENTS_FOR_PAIRING =
      "Device failed trust requirements for pairing: ";

  public static final String DEVICE_ID_IS_REQUIRED_FOR_RECOVERY =
      "Device id is required for recovery";

  public static final String DEVICE_IS_BLOCKED_DUE_TO_TRUST_VIOLATIONS =
      "Device is blocked due to trust violations";

  public static final String DEVICE_LIMIT_REACHED_FOR_PLAN = "Device limit reached for plan ";

  public static final String DEVICE_MONITORING_REVOKED_RE_PAIR_REQUIRED =
      "Device monitoring revoked — re-pair required";

  public static final String DEVICE_NOT_FOUND = "Device not found";

  public static final String DEVICE_SUSPENDED = "Device suspended";

  public static final String DEVICE_TOKEN_REVOKED_RE_PAIR_REQUIRED =
      "Device token revoked — re-pair required";

  public static final String DEVICE_TRUST_IS_INSUFFICIENT_FOR_THIS_ACTION =
      "Device trust is insufficient for this action";

  public static final String DEVICE_TRUST_SCORE_TOO_LOW_FOR_ACTIVE_SESSION =
      "Device trust score too low for active session";

  public static final String DUPLICATE_REQUEST_NONCE = "Duplicate request nonce";

  public static final String DUPLICATE_SIGNALING_MESSAGE_REJECTED =
      "Duplicate signaling message rejected";

  public static final String
      EMAIL_ADDRESS_NOT_VERIFIED_PLEASE_CHECK_YOUR_INBOX_FOR_A_VERIFICATION_LINK =
          "Email address not verified — please check your inbox for a verification link";

  public static final String EMAIL_ALREADY_REGISTERED = "Email already registered";

  public static final String HMAC_SIGNING_FAILED = "HMAC signing failed";

  public static final String INCORRECT_PASSWORD = "Incorrect password";

  public static final String INVALID_STRIPE_WEBHOOK_SIGNATURE = "Invalid Stripe webhook signature";

  public static final String INVALID_STRIPE_SIGNATURE_FORMAT = "Invalid Stripe-Signature format";

  public static final String INVALID_STRIPE_SIGNATURE_TIMESTAMP =
      "Invalid Stripe-Signature timestamp";

  public static final String INVALID_X_DEVICE_TIMESTAMP = "Invalid X-Device-Timestamp";

  public static final String INVALID_DEVICE_SIGNATURE = "Invalid device signature";

  public static final String INVALID_DEVICE_TOKEN = "Invalid device token";

  public static final String INVALID_OR_EXPIRED_RESET_LINK = "Invalid or expired reset link";

  public static final String INVALID_OR_EXPIRED_VERIFICATION_LINK =
      "Invalid or expired verification link";

  public static final String INVALID_PARENT_PIN = "Invalid parent PIN";

  public static final String INVALID_REFRESH_TOKEN = "Invalid refresh token";

  public static final String INVALID_SETUP_CODE = "Invalid setup code";

  public static final String INVALID_SIGNALING_ROOM_ID = "Invalid signaling room id";

  public static final String INVALID_SIGNALING_ROOM_ID_SESSION_REQUIRED =
      "Invalid signaling room id — session required";

  public static final String INVALID_TOKEN_TYPE = "Invalid token type";

  public static final String INVALID_WEBHOOK_PAYLOAD = "Invalid webhook payload";

  public static final String KIOSK_MODE_REQUIRES_DEVICE_OWNER_ON_THE_CHILD_DEVICE =
      "Kiosk mode requires Device Owner on the child device";

  public static final String LIVE_STREAMING_REQUIRES_STANDARD_OR_FAMILY_PLAN =
      "Live streaming requires Standard or Family plan";

  public static final String MISSING_HMAC_HEADERS = "Missing HMAC headers";

  public static final String MISSING_STRIPE_EVENT_ID = "Missing Stripe event id";

  public static final String MISSING_STRIPE_SIGNATURE_HEADER = "Missing Stripe-Signature header";

  public static final String
      MULTIPLE_ACTIVE_SETUP_SESSIONS_MATCH_THIS_PASSWORD_USE_RECOVERY_WITH_YOUR_SETUP_ =
          "Multiple active setup sessions match this password. Use recovery with your setup code.";

  public static final String MULTIPLE_DEVICES_MATCH_THIS_PASSWORD_RE_OPEN_THE_APP_AND_TRY_AGAIN =
      "Multiple devices match this password. Re-open the app and try again.";

  public static final String NO_ACTIVE_CAMERA_STREAM = "No active camera stream";

  public static final String
      NO_ACTIVE_RECOVERY_CODE_TAP_FORGOT_PASSWORD_AGAIN_TO_REQUEST_A_NEW_CODE =
          "No active recovery code. Tap Forgot password again to request a new code.";

  public static final String NO_ACTIVE_VERIFICATION_CODE_TAP_GET_VERIFICATION_CODE_AGAIN =
      "No active verification code. Tap Get verification code again.";

  public static final String NONCE_STORE_UNAVAILABLE = "Nonce store unavailable";

  public static final String NOT_FOUND = "Not found";

  public static final String PIN_MUST_BE_AT_LEAST_4_CHARACTERS =
      "PIN must be at least 4 characters";
  public static final String PIN_MUST_BE_AT_LEAST_8_CHARACTERS_WITH_LETTER_AND_DIGIT =
      "Parent password must be at least 8 characters and include a letter and a number";

  public static final String PIN_OR_VERIFICATION_CODE_IS_REQUIRED =
      "PIN or verification code is required";

  public static final String PAIRING_CODE_ALREADY_USED = "Pairing code already used";

  public static final String PAIRING_CODE_NOT_FOUND = "Pairing code not found";

  public static final String
      PARENT_PIN_IS_NOT_CONFIGURED_OPEN_THE_PARENT_APP_SETTINGS_SET_PARENT_PIN =
          "Parent PIN is not configured. Open the parent app → Settings → Set Parent PIN ";

  public static final String PARENT_PIN_NOT_SET = "Parent PIN not set";

  public static final String PARENT_PIN_REQUIRED_FOR = "Parent PIN required for ";

  public static final String PARENT_ACCOUNT_NOT_FOUND = "Parent account not found";

  public static final String
      PARENT_PASSWORD_IS_NOT_CONFIGURED_USE_THE_VERIFICATION_CODE_FROM_THE_PARENT_APP =
          "Parent password is not configured. Use the verification code from the parent app.";

  public static final String PARENT_PROFILE_NOT_FOUND = "Parent profile not found";

  public static final String PASSWORD_RESET_IS_DISABLED = "Password reset is disabled";

  public static final String PLAY_INTEGRITY_NONCE_EXPIRED = "Play Integrity nonce expired";

  public static final String PLAY_INTEGRITY_PACKAGE_MISMATCH = "Play Integrity package mismatch";

  public static final String PLAY_INTEGRITY_TOKEN_REQUIRED = "Play Integrity token required";

  public static final String PLAY_INTEGRITY_VERIFICATION_FAILED =
      "Play Integrity verification failed";

  public static final String POLICY_NOT_FOUND = "Policy not found";

  public static final String RATE_LIMIT_EXCEEDED = "Rate limit exceeded";

  public static final String REFRESH_TOKEN_EXPIRED = "Refresh token expired";

  public static final String
      REMOTE_CONTROL_REQUIRES_ACCESSIBILITY_TO_BE_ENABLED_ON_THE_CHILD_DEVICE =
          "Remote control requires Accessibility to be enabled on the child device. ";

  public static final String REQUEST_ALREADY_HANDLED = "Request already handled";

  public static final String REQUEST_TIMESTAMP_OUTSIDE_ALLOWED_WINDOW =
      "Request timestamp outside allowed window";

  public static final String SCREEN_SHARING_DISABLED_BY_POLICY =
      "Screen sharing disabled by policy";

  public static final String SERVICE_TEMPORARILY_UNAVAILABLE = "Service temporarily unavailable";

  public static final String SESSION_DOES_NOT_BELONG_TO_SIGNALING_ROOM =
      "Session does not belong to signaling room";

  public static final String SESSION_EXPIRED_SIGN_IN_AGAIN = "Session expired — sign in again";

  public static final String SESSION_NOT_FOUND = "Session not found";

  public static final String
      SET_YOUR_FAMILY_CHILD_LOGIN_PASSWORD_IN_SETTINGS_BEFORE_GENERATING_A_PAIRING_COD =
          "Set your family child login password in Settings before generating a pairing code";

  public static final String SETUP_CODE_ALREADY_USED = "Setup code already used";

  public static final String SETUP_CODE_EXPIRED = "Setup code expired";

  public static final String SETUP_CODE_IS_REQUIRED = "Setup code is required";

  public static final String SIGNALING_UNAVAILABLE_STREAMING_SESSION_HAS_ENDED =
      "Signaling unavailable — streaming session has ended";

  public static final String STREAMING_SESSION_IS_NOT_ACTIVE = "Streaming session is not active";

  public static final String STREAMING_SESSION_NOT_FOUND = "Streaming session not found";

  public static final String STRIPE_CHECKOUT_SESSION_FAILED = "Stripe checkout session failed";

  public static final String STRIPE_PRICE_ID_NOT_CONFIGURED_FOR_PLAN =
      "Stripe price ID not configured for plan";

  public static final String STRIPE_WEBHOOK_VERIFICATION_IS_NOT_CONFIGURED =
      "Stripe webhook verification is not configured";

  public static final String UNABLE_TO_START_CHECKOUT_PLEASE_TRY_AGAIN_LATER =
      "Unable to start checkout. Please try again later.";

  public static final String USER_NOT_FOUND = "User not found";

  public static final String VERIFY_YOUR_EMAIL_ADDRESS_TO_START_YOUR_FREE_TRIAL =
      "Verify your email address to start your free trial.";

  public static final String WEBHOOK_TIMESTAMP_TOO_OLD = "Webhook timestamp too old";

  public static final String WEBHOOK_VERIFICATION_FAILED = "Webhook verification failed";

  public static final String SESSIONID_DOES_NOT_MATCH_SIGNALING_ROOM =
      "sessionId does not match signaling room";

  public static final String SESSIONID_IS_REQUIRED_FOR_SIGNALING =
      "sessionId is required for signaling";

  public static final String IF_AN_ACCOUNT_EXISTS_FOR_THAT_EMAIL_A_RESET_LINK_HAS_BEEN_SENT =
      "If an account exists for that email, a reset link has been sent.";

  public static final String
      IF_THIS_DEVICE_IS_PAIRED_A_RECOVERY_CODE_HAS_BEEN_SENT_TO_THE_PARENT_APP =
          "If this device is paired, a recovery code has been sent to the parent app. Open Alerts to view it.";

  public static final String CHAT_ROOM_NOT_FOUND = "Chat room not found";

  public static final String CHAT_INVITE_NOT_FOUND = "Chat invite not found";

  public static final String CHAT_NOT_A_MEMBER = "You are not a member of this chat room";

  public static final String CHAT_ONLY_OWNER_CAN_DELETE = "Only the room owner can delete this room";

  public static final String CHAT_ONLY_OWNER_CAN_CLEAR =
      "Only the room owner can clear chat history";

  public static final String CHAT_ONLY_OWNER_CAN_DECRYPT =
      "Only the room owner can decrypt this chat";

  public static final String CHAT_DECRYPT_NOT_CONFIGURED =
      "Chat decrypt is not configured on this server";

  public static final String CHAT_DECRYPT_PIN_INVALID = "Invalid decrypt PIN";

  public static final String CHAT_OWNER_CANNOT_LEAVE =
      "Room owners must delete the room instead of leaving";

  public static final String CHAT_MESSAGE_NOT_FOUND = "Message not found";

  public static final String CHAT_MESSAGE_BODY_REQUIRED = "Message cannot be empty";

  public static final String CHAT_CANNOT_DELETE_MESSAGE =
      "You can only delete your own messages for everyone";

  public static final String CHAT_CANNOT_DELETE_FOR_EVERYONE =
      "Only the sender can delete a message for everyone";

  public static final String CHAT_CANNOT_EDIT_MESSAGE = "You can only edit your own messages";

  public static final String CHAT_CANNOT_INVITE_YOURSELF = "You cannot invite yourself";

  public static final String CHAT_USER_ALREADY_IN_ROOM = "That parent is already in this room";

  public static final String CHAT_INVITE_ALREADY_PENDING =
      "An invite is already pending for that parent";

  public static final String CHAT_INVITE_ALREADY_RESPONDED = "This invite has already been responded to";

  public static final String CHAT_ATTACHMENT_REQUIRED = "A file attachment is required";

  public static final String CHAT_ATTACHMENT_TYPE_NOT_ALLOWED =
      "Only JPEG, PNG, WebP, GIF images and MP4, WebM, or MOV videos are allowed";

  public static final String CHAT_ATTACHMENT_TOO_LARGE = "Attachment exceeds the size limit";

  public static final String CHAT_ATTACHMENT_NOT_FOUND = "Attachment not found";

  public static final String CHAT_ATTACHMENT_STORE_FAILED = "Could not store attachment";

  public static final String CHAT_ATTACHMENT_READ_FAILED = "Could not read attachment";

  public static final String CHAT_ATTACHMENT_ALREADY_VIEWED =
      "This view-once attachment was already opened";

  public static final String CHAT_SENDER_CANNOT_VIEW_ONCE =
      "View-once media can only be opened by recipients";

  public static final String CHAT_ATTACHMENT_USE_VIEW_ENDPOINT =
      "Use the view endpoint to open this attachment";

  public static final String CHAT_CAPTION_TOO_LONG = "Caption is too long (max 2000 characters)";

  public static final String CHAT_REPLY_MESSAGE_NOT_FOUND =
      "The message you are replying to was not found";

  public static final String CHAT_REACTION_EMOJI_REQUIRED = "Reaction emoji is required";

  public static final String CHAT_REACTION_EMOJI_INVALID = "Reaction emoji is invalid";

  private MessageConstants() {}
}
