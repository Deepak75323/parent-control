import {promptParentFingerprint} from '../services/parentBiometric';

describe('promptParentFingerprint', () => {
  it('treats a missing native module as unavailable so password can still be asked', async () => {
    await expect(promptParentFingerprint()).resolves.toBe('unavailable');
  });
});
