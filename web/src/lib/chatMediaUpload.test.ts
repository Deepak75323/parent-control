import {describe, expect, it} from 'vitest';
import {
  CHAT_MAX_IMAGE_BYTES,
  CHAT_MAX_VIDEO_BYTES,
  validateChatMediaFile,
} from './chatMediaUpload';

describe('validateChatMediaFile', () => {
  it('accepts supported image types within size limit', () => {
    const file = new File(['x'], 'photo.png', {type: 'image/png'});
    Object.defineProperty(file, 'size', {value: CHAT_MAX_IMAGE_BYTES});
    expect(validateChatMediaFile(file)).toBeNull();
  });

  it('rejects oversized images', () => {
    const file = new File(['x'], 'photo.png', {type: 'image/png'});
    Object.defineProperty(file, 'size', {value: CHAT_MAX_IMAGE_BYTES + 1});
    expect(validateChatMediaFile(file)).toMatch(/10 MB/);
  });

  it('accepts supported video types within size limit', () => {
    const file = new File(['x'], 'clip.mp4', {type: 'video/mp4'});
    Object.defineProperty(file, 'size', {value: CHAT_MAX_VIDEO_BYTES});
    expect(validateChatMediaFile(file)).toBeNull();
  });

  it('rejects unsupported file types', () => {
    const file = new File(['x'], 'notes.pdf', {type: 'application/pdf'});
    expect(validateChatMediaFile(file)).toMatch(/Only JPEG/);
  });
});
