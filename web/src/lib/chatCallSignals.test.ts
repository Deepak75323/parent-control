import {describe, expect, it, vi} from 'vitest';
import {dispatchChatCallSignal, subscribeChatCallSignals} from './chatCallSignals';

describe('chatCallSignals', () => {
  it('delivers every signal to subscribers without dropping', () => {
    const seen: string[] = [];
    const unsubscribe = subscribeChatCallSignals(event => {
      seen.push(event.messageType ?? '');
    });

    dispatchChatCallSignal({
      eventType: 'CALL_SIGNAL',
      roomId: 'r',
      callId: 'c',
      messageType: 'OFFER',
      seq: 1,
    });
    dispatchChatCallSignal({
      eventType: 'CALL_SIGNAL',
      roomId: 'r',
      callId: 'c',
      messageType: 'ICE',
      seq: 2,
    });
    dispatchChatCallSignal({
      eventType: 'CALL_SIGNAL',
      roomId: 'r',
      callId: 'c',
      messageType: 'ANSWER',
      seq: 3,
    });

    expect(seen).toEqual(['OFFER', 'ICE', 'ANSWER']);
    unsubscribe();
    dispatchChatCallSignal({
      eventType: 'CALL_SIGNAL',
      roomId: 'r',
      callId: 'c',
      messageType: 'ICE',
      seq: 4,
    });
    expect(seen).toEqual(['OFFER', 'ICE', 'ANSWER']);
  });

  it('isolates handler failures', () => {
    const ok = vi.fn();
    const unsubscribeBad = subscribeChatCallSignals(() => {
      throw new Error('boom');
    });
    const unsubscribeOk = subscribeChatCallSignals(ok);

    expect(() =>
      dispatchChatCallSignal({
        eventType: 'CALL_SIGNAL',
        roomId: 'r',
        callId: 'c',
        messageType: 'ICE',
        seq: 1,
      }),
    ).not.toThrow();
    expect(ok).toHaveBeenCalledTimes(1);

    unsubscribeBad();
    unsubscribeOk();
  });
});
