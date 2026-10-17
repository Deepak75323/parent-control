import {describe, expect, it} from 'vitest';
import {formatReplyPreviewText, replyPreviewFromMessage} from './chatReplyPreview';
import type {ChatMessageItem} from '../types/api';

describe('chatReplyPreview', () => {
  it('labels deleted quoted messages', () => {
    expect(
      formatReplyPreviewText({
        messageId: 'm1',
        senderLabel: '@x',
        messageType: 'TEXT',
        bodyPreview: '',
        deleted: true,
      }),
    ).toBe('This note is deleted');
  });

  it('builds preview from a media message without caption', () => {
    const message: ChatMessageItem = {
      id: 'm2',
      roomId: 'r1',
      senderUserId: 'u1',
      senderLabel: '@parent',
      messageType: 'IMAGE',
      body: '',
      createdAt: '2026-09-02T00:00:00Z',
    };
    expect(replyPreviewFromMessage(message).bodyPreview).toBe('Photo');
  });
});
