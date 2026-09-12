import {describe, expect, it} from 'vitest';
import {isWebAuthnAvailable} from './webAuthnParentGate';

describe('webAuthnParentGate', () => {
  it('reports availability without throwing', () => {
    expect(typeof isWebAuthnAvailable()).toBe('boolean');
  });
});
