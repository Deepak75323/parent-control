import {describe, expect, it} from 'vitest';
import {
  isDeletedMessagesPinDigits,
  validateDeletedMessagesPinForm,
} from './deletedMessagesPin';

describe('deletedMessagesPin', () => {
  it('accepts 4–8 digit codes only', () => {
    expect(isDeletedMessagesPinDigits('1234')).toBe(true);
    expect(isDeletedMessagesPinDigits('12345678')).toBe(true);
    expect(isDeletedMessagesPinDigits('12')).toBe(false);
    expect(isDeletedMessagesPinDigits('12ab')).toBe(false);
  });

  it('validates create confirm match', () => {
    expect(validateDeletedMessagesPinForm('create', {pin: '1234', confirmPin: '1234'})).toBeNull();
    expect(validateDeletedMessagesPinForm('create', {pin: '1234', confirmPin: '9999'})).toMatch(
      /match/i,
    );
  });

  it('requires current PIN when changing', () => {
    expect(
      validateDeletedMessagesPinForm('change', {
        currentPin: '',
        pin: '5678',
        confirmPin: '5678',
      }),
    ).toMatch(/current/i);
  });
});
