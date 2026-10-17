import {describe, expect, it} from 'vitest';
import {describeWebAuthnError, isWebAuthnAvailable} from './webAuthnParentGate';

describe('webAuthnParentGate', () => {
  it('reports availability without throwing', () => {
    expect(typeof isWebAuthnAvailable()).toBe('boolean');
  });

  it('maps credential-manager failures to actionable messages', () => {
    expect(describeWebAuthnError(new DOMException('x', 'NotAllowedError'))).toMatch(/dismissed/i);
    expect(describeWebAuthnError(new DOMException('x', 'SecurityError'))).toMatch(/HTTPS/i);
    expect(describeWebAuthnError(new DOMException('x', 'NotSupportedError'))).toMatch(
      /no fingerprint/i,
    );
    expect(describeWebAuthnError(new Error('An unknown error occurred'))).toMatch(
      /decrypt code/i,
    );
  });
});
