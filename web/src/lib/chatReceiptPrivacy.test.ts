import {describe, expect, it} from 'vitest';
import {receiptStatusForViewer} from './chatReceiptPrivacy';

describe('receiptStatusForViewer', () => {
  it('leaves READ when viewer has receipts enabled', () => {
    expect(receiptStatusForViewer('READ', true)).toBe('READ');
    expect(receiptStatusForViewer('READ', undefined)).toBe('READ');
  });

  it('caps READ at DELIVERED when viewer has receipts disabled', () => {
    expect(receiptStatusForViewer('READ', false)).toBe('DELIVERED');
    expect(receiptStatusForViewer('DELIVERED', false)).toBe('DELIVERED');
    expect(receiptStatusForViewer('SENT', false)).toBe('SENT');
  });
});
