/** 4–8 digit camera unlock password — separate from the chat decrypt code. */
export const CAMERA_PASSWORD_PATTERN = /^\d{4,8}$/;

export type CameraPasswordFormMode = 'create' | 'change' | 'unlock';

export function isCameraPasswordDigits(value: string): boolean {
  return CAMERA_PASSWORD_PATTERN.test(value.trim());
}

/** Client-side validation before calling Settings create / change. */
export function validateCameraPasswordForm(
  mode: Exclude<CameraPasswordFormMode, 'unlock'>,
  fields: {currentPin?: string; pin: string; confirmPin: string},
): string | null {
  if (mode === 'change' && !isCameraPasswordDigits(fields.currentPin ?? '')) {
    return 'Enter your current 4–8 digit camera password';
  }
  if (!isCameraPasswordDigits(fields.pin)) {
    return 'New camera password must be 4–8 digits';
  }
  if (fields.pin.trim() !== fields.confirmPin.trim()) {
    return 'New password and confirmation do not match';
  }
  return null;
}
