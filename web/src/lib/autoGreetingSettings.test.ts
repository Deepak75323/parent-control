import {describe, expect, it} from 'vitest';
import {validateAutoGreetingForm} from './autoGreetingSettings';

describe('validateAutoGreetingForm', () => {
  it('requires a room when enabling', () => {
    expect(
      validateAutoGreetingForm({
        enabled: true,
        roomId: '',
        morningStart: '07:05',
        morningEnd: '09:00',
        eveningStart: '23:55',
        eveningEnd: '00:00',
        morningMessage: 'good morning baby',
        eveningMessage: 'good night baby',
      }),
    ).toMatch(/chat room/i);
  });

  it('requires morning and evening messages when enabling', () => {
    expect(
      validateAutoGreetingForm({
        enabled: true,
        roomId: 'room-1',
        morningStart: '07:05',
        morningEnd: '09:00',
        eveningStart: '23:55',
        eveningEnd: '00:00',
        morningMessage: '  ',
        eveningMessage: 'good night baby',
      }),
    ).toMatch(/morning message/i);
  });

  it('accepts a full morning range and custom messages', () => {
    expect(
      validateAutoGreetingForm({
        enabled: true,
        roomId: 'room-1',
        morningStart: '07:05',
        morningEnd: '09:00',
        eveningStart: '23:55',
        eveningEnd: '00:00',
        morningMessage: 'rise and shine',
        eveningMessage: 'sweet dreams',
      }),
    ).toBeNull();
  });
});
