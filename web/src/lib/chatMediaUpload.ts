export const CHAT_MAX_IMAGE_BYTES = 10 * 1024 * 1024;
export const CHAT_MAX_VIDEO_BYTES = 50 * 1024 * 1024;
/** Max view-once media files staged in the composer at once. */
export const CHAT_MAX_PENDING_MEDIA_FILES = 10;

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

export type AcceptChatMediaFilesResult = {
  files: File[];
  error: string | null;
};

/**
 * Validates and merges newly picked media into the composer queue.
 * Each file becomes its own view-once message on send.
 */
export function acceptChatMediaFiles(
  incoming: Iterable<File>,
  existing: readonly File[] = [],
  maxFiles = CHAT_MAX_PENDING_MEDIA_FILES,
): AcceptChatMediaFilesResult {
  const next = [...existing];
  let error: string | null = null;

  for (const file of incoming) {
    if (next.length >= maxFiles) {
      error = `You can attach up to ${maxFiles} view-once photos or videos at a time`;
      break;
    }
    const validationError = validateChatMediaFile(file);
    if (validationError) {
      error = validationError;
      continue;
    }
    if (file.size === 0) {
      error = 'Selected file is empty — pick the image again';
      continue;
    }
    next.push(file);
  }

  return {files: next, error};
}
