import {describe, expect, it} from 'vitest';
import {isPortalBootstrapAdmin} from './portalAccess';

describe('portalAccess', () => {
  it('recognizes the bootstrap admin email', () => {
    expect(isPortalBootstrapAdmin('anilkumar@gmail.com')).toBe(true);
    expect(isPortalBootstrapAdmin('AnilKumar@Gmail.com')).toBe(true);
    expect(isPortalBootstrapAdmin('other@example.com')).toBe(false);
    expect(isPortalBootstrapAdmin(null)).toBe(false);
  });
});
