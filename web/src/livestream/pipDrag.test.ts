import {describe, expect, it} from 'vitest';
import {clamp, clampPipPosition} from './pipDrag';

describe('clamp', () => {
  it('limits values to the given range', () => {
    expect(clamp(5, 0, 10)).toBe(5);
    expect(clamp(-1, 0, 10)).toBe(0);
    expect(clamp(99, 0, 10)).toBe(10);
  });
});

describe('clampPipPosition', () => {
  it('keeps the panel inside drag bounds', () => {
    const original = globalThis.window;
    Object.defineProperty(globalThis, 'window', {
      configurable: true,
      value: {
        innerHeight: 400,
        visualViewport: {offsetTop: 0, height: 400},
      },
    });

    const next = clampPipPosition(500, -40, 240, 228);
    expect(next.x).toBe(152);
    expect(next.y).toBe(8);

    Object.defineProperty(globalThis, 'window', {
      configurable: true,
      value: original,
    });
  });
});
