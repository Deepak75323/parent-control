import {describe, expect, it} from 'vitest';
import {getAvatarColor, getAvatarInitials} from './avatarUtils';

describe('getAvatarInitials', () => {
  it('returns two letters from first and last name', () => {
    expect(getAvatarInitials('Golden Lily')).toBe('GL');
  });

  it('returns up to two chars for single word', () => {
    expect(getAvatarInitials('Aurora')).toBe('AU');
  });
});

describe('getAvatarColor', () => {
  it('is deterministic for the same seed', () => {
    expect(getAvatarColor('user-123')).toBe(getAvatarColor('user-123'));
  });

  it('differs for different seeds', () => {
    expect(getAvatarColor('user-a')).not.toBe(getAvatarColor('user-b'));
  });
});
