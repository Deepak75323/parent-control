/**
 * Normal chat hides notes created at/before clear-history (or personal clear).
 * Owner "show deleted messages" must ignore those watermarks so soft-deleted
 * clear-history notes stay reviewable (API already returns them).
 */
export function isHiddenByChatClearWatermark(options: {
  createdMs: number;
  globalClearedAtMs?: number;
  personalClearedAtMs?: number;
  ownerDeletedView: boolean;
}): boolean {
  if (options.ownerDeletedView) {
    return false;
  }
  if (options.globalClearedAtMs && options.createdMs <= options.globalClearedAtMs) {
    return true;
  }
  if (options.personalClearedAtMs && options.createdMs <= options.personalClearedAtMs) {
    return true;
  }
  return false;
}
