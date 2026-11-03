import {describe, expect, it} from 'vitest';
import {isCameraPasswordDigits, validateCameraPasswordForm} from './cameraPassword';

describe('isCameraPasswordDigits', () => {
  it('accepts 4–8 digits only', () => {
    expect(isCameraPasswordDigits('1234')).toBe(true);
    expect(isCameraPasswordDigits('12345678')).toBe(true);
    expect(isCameraPasswordDigits('123')).toBe(false);
    expect(isCameraPasswordDigits('123456789')).toBe(false);
    expect(isCameraPasswordDigits('12a4')).toBe(false);
  });
});

describe('validateCameraPasswordForm', () => {
  it('create: needs matching new + confirm', () => {
    expect(
      validateCameraPasswordForm('create', {pin: '1234', confirmPin: '1234'}),
    ).toBeNull();
    expect(validateCameraPasswordForm('create', {pin: '1234', confirmPin: '9999'})).toBe(
      'New password and confirmation do not match',
    );
    expect(validateCameraPasswordForm('create', {pin: '12', confirmPin: '12'})).toBe(
      'New camera password must be 4–8 digits',
    );
  });

  it('change: requires the current password too', () => {
    expect(
      validateCameraPasswordForm('change', {pin: '2222', confirmPin: '2222'}),
    ).toBe('Enter your current 4–8 digit camera password');
    expect(
      validateCameraPasswordForm('change', {currentPin: '1111', pin: '2222', confirmPin: '2222'}),
    ).toBeNull();
  });
});
