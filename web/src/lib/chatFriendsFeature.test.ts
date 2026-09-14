import {describe, expect, it} from 'vitest';
import {isChatDmsEnabled, isChatFriendsEnabled} from './chatFriendsFeature';

describe('isChatFriendsEnabled', () => {
  it('is off by default', () => {
    expect(isChatFriendsEnabled(undefined)).toBe(false);
    expect(isChatFriendsEnabled('')).toBe(false);
    expect(isChatFriendsEnabled('false')).toBe(false);
    expect(isChatFriendsEnabled('0')).toBe(false);
  });

  it('turns on only for true', () => {
    expect(isChatFriendsEnabled('true')).toBe(true);
    expect(isChatFriendsEnabled('TRUE')).toBe(true);
    expect(isChatFriendsEnabled(' true ')).toBe(true);
  });
});

describe('isChatDmsEnabled', () => {
  it('is off by default', () => {
    expect(isChatDmsEnabled(undefined)).toBe(false);
    expect(isChatDmsEnabled('false')).toBe(false);
  });

  it('turns on only for true', () => {
    expect(isChatDmsEnabled('true')).toBe(true);
  });
});
