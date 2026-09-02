import type {ChatMessageItem, ChatMessageReplyPreview} from '../types/api';
import {resolveChatDisplayNickname} from './chatDisplayNickname';

export function formatReplyPreviewText(preview: ChatMessageReplyPreview): string {
  if (preview.deleted) {
    return 'Message deleted';
  }
  if (preview.bodyPreview?.trim()) {
    return preview.bodyPreview;
  }
  if (preview.messageType === 'IMAGE') {
    return 'Photo';
  }
  if (preview.messageType === 'VIDEO') {
    return 'Video';
  }
  return 'Message';
}

export function replyPreviewFromMessage(message: ChatMessageItem): ChatMessageReplyPreview {
  const messageType = message.messageType ?? 'TEXT';
  let bodyPreview = message.body?.trim() ?? '';
  if (!bodyPreview && messageType === 'IMAGE') {
    bodyPreview = 'Photo';
  }
  if (!bodyPreview && messageType === 'VIDEO') {
    bodyPreview = 'Video';
  }
  if (bodyPreview.length > 240) {
    bodyPreview = `${bodyPreview.slice(0, 237)}…`;
  }

  return {
    messageId: message.id,
    senderLabel: message.senderLabel,
    senderNickname: message.senderNickname,
    messageType,
    bodyPreview,
    deleted: false,
  };
}

export function replyPreviewSenderName(preview: ChatMessageReplyPreview): string {
  return resolveChatDisplayNickname(preview.senderNickname, preview.senderLabel);
}
