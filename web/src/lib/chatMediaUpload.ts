export const CHAT_MAX_IMAGE_BYTES = 10 * 1024 * 1024;
export const CHAT_MAX_VIDEO_BYTES = 50 * 1024 * 1024;

export const CHAT_IMAGE_MIME_TYPES = new Set([
  'image/jpeg',
  'image/png',
  'image/webp',
  'image/gif',
]);

export const CHAT_VIDEO_MIME_TYPES = new Set([
  'video/mp4',
  'video/webm',
  'video/quicktime',
]);

export function validateChatMediaFile(file: File): string | null {
  const mime = file.type.toLowerCase();
  if (CHAT_IMAGE_MIME_TYPES.has(mime)) {
    return file.size > CHAT_MAX_IMAGE_BYTES ? 'Image must be 10 MB or smaller' : null;
  }
  if (CHAT_VIDEO_MIME_TYPES.has(mime)) {
    return file.size > CHAT_MAX_VIDEO_BYTES ? 'Video must be 50 MB or smaller' : null;
  }
  return 'Only JPEG, PNG, WebP, GIF images or MP4, WebM, MOV videos are allowed';
}
