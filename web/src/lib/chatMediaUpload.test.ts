import {describe, expect, it} from 'vitest';
import {
  CHAT_MAX_IMAGE_BYTES,
  CHAT_MAX_PENDING_MEDIA_FILES,
  CHAT_MAX_VIDEO_BYTES,
  acceptChatMediaFiles,
  validateChatMediaFile,
} from './chatMediaUpload';

function png(name: string, size = 16): File {
  const file = new File(['x'], name, {type: 'image/png'});
  Object.defineProperty(file, 'size', {value: size});
  return file;
}

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

describe('acceptChatMediaFiles', () => {
  it('queues several valid view-once photos', () => {
    const result = acceptChatMediaFiles([png('a.png'), png('b.png'), png('c.png')]);
    expect(result.error).toBeNull();
    expect(result.files.map(f => f.name)).toEqual(['a.png', 'b.png', 'c.png']);
  });

  it('appends to files already staged', () => {
    const result = acceptChatMediaFiles([png('b.png')], [png('a.png')]);
    expect(result.error).toBeNull();
    expect(result.files.map(f => f.name)).toEqual(['a.png', 'b.png']);
  });

  it('keeps valid files and reports the first invalid type', () => {
    const bad = new File(['x'], 'notes.pdf', {type: 'application/pdf'});
    const result = acceptChatMediaFiles([png('ok.png'), bad]);
    expect(result.files.map(f => f.name)).toEqual(['ok.png']);
    expect(result.error).toMatch(/Only JPEG/);
  });

  it('caps the composer queue', () => {
    const existing = Array.from({length: CHAT_MAX_PENDING_MEDIA_FILES}, (_, i) =>
      png(`have-${i}.png`),
    );
    const result = acceptChatMediaFiles([png('extra.png')], existing);
    expect(result.files).toHaveLength(CHAT_MAX_PENDING_MEDIA_FILES);
    expect(result.error).toMatch(/up to 10/);
  });
});
