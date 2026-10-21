/** 4–8 digit PIN for reviewing soft-deleted Notes (owner view). */
export const DELETED_MESSAGES_PIN_PATTERN = /^\d{4,8}$/;

export type DeletedMessagesPinFormMode = 'create' | 'change' | 'reset' | 'unlock';

export function isDeletedMessagesPinDigits(value: string): boolean {
  return DELETED_MESSAGES_PIN_PATTERN.test(value.trim());
}

export function deletedMessagesPinMismatch(pin: string, confirmPin: string): boolean {
  return pin.trim() !== confirmPin.trim();
}

/** Client-side validation before calling Settings create / change / reset. */
export function validateDeletedMessagesPinForm(
  mode: Exclude<DeletedMessagesPinFormMode, 'unlock'>,
  fields: {currentPin?: string; pin: string; confirmPin: string},
): string | null {
  if (mode === 'change' && !isDeletedMessagesPinDigits(fields.currentPin ?? '')) {
    return 'Enter your current 4–8 digit PIN';
  }
  if (!isDeletedMessagesPinDigits(fields.pin)) {
    return 'New PIN must be 4–8 digits';
  }
  if (deletedMessagesPinMismatch(fields.pin, fields.confirmPin)) {
    return 'New PIN and confirmation do not match';
  }
  return null;
}
