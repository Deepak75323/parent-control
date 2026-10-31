import {describe, expect, it} from 'vitest';
import {isHiddenByChatClearWatermark} from './chatClearWatermark';

describe('isHiddenByChatClearWatermark', () => {
  const createdMs = Date.parse('2026-10-31T09:00:00Z');
  const clearedMs = Date.parse('2026-10-31T09:30:00Z');

  it('hides notes at or before the clear watermark in normal chat', () => {
    expect(
      isHiddenByChatClearWatermark({
        createdMs,
        globalClearedAtMs: clearedMs,
        personalClearedAtMs: undefined,
        ownerDeletedView: false,
      }),
    ).toBe(true);
  });

  it('keeps clear-history notes visible in owner show-deleted view', () => {
    expect(
      isHiddenByChatClearWatermark({
        createdMs,
        globalClearedAtMs: clearedMs,
        personalClearedAtMs: clearedMs,
        ownerDeletedView: true,
      }),
    ).toBe(false);
  });

  it('keeps notes after the watermark', () => {
    expect(
      isHiddenByChatClearWatermark({
        createdMs: clearedMs + 1,
        globalClearedAtMs: clearedMs,
        personalClearedAtMs: undefined,
        ownerDeletedView: false,
      }),
    ).toBe(false);
  });
});
