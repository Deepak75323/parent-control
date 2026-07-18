import {
  appDisplayName,
  friendlyPackageLabel,
  looksLikeAndroidPackage,
} from '../utils/appDisplayName';

describe('appDisplayName', () => {
  it('detects Android package ids', () => {
    expect(looksLikeAndroidPackage('com.miui.screenshot')).toBe(true);
    expect(looksLikeAndroidPackage('Screenshot')).toBe(false);
    expect(looksLikeAndroidPackage('New message')).toBe(false);
  });

  it('humanizes package ids', () => {
    expect(friendlyPackageLabel('com.miui.screenshot')).toBe('Screenshot');
    expect(friendlyPackageLabel('com.google.android.youtube')).toBe('YouTube');
  });

  it('never returns a raw package id for display', () => {
    expect(
      appDisplayName({
        title: 'com.miui.screenshot',
        packageName: 'com.miui.screenshot',
        fallback: 'Notification',
      }),
    ).toBe('Screenshot');
    expect(
      appDisplayName({
        title: null,
        packageName: 'com.miui.screenshot',
      }),
    ).toBe('Screenshot');
    expect(
      appDisplayName({
        appLabel: 'Gallery',
        packageName: 'com.miui.gallery',
      }),
    ).toBe('Gallery');
    expect(
      appDisplayName({
        title: 'Battery low',
        packageName: 'com.android.systemui',
      }),
    ).toBe('Battery low');
  });
});
