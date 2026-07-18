/**
 * Parent-facing app names. Never show raw Android package IDs (e.g. com.miui.screenshot)
 * in the UI — parents only need a readable label.
 */

const KNOWN_PACKAGE_LABELS: Record<string, string> = {
  'com.google.android.youtube': 'YouTube',
  'com.google.android.apps.youtube.kids': 'YouTube Kids',
  'com.whatsapp': 'WhatsApp',
  'com.instagram.android': 'Instagram',
  'com.facebook.katana': 'Facebook',
  'com.facebook.orca': 'Messenger',
  'com.snapchat.android': 'Snapchat',
  'com.twitter.android': 'X',
  'com.zhiliaoapp.musically': 'TikTok',
  'com.ss.android.ugc.trill': 'TikTok',
  'com.android.chrome': 'Chrome',
  'com.android.vending': 'Play Store',
  'com.android.settings': 'Settings',
  'com.android.systemui': 'System UI',
  'com.miui.screenshot': 'Screenshot',
  'com.miui.gallery': 'Gallery',
  'com.miui.home': 'Home',
  'com.android.mms': 'Messages',
  'com.google.android.apps.messaging': 'Messages',
  'com.google.android.gm': 'Gmail',
  'com.spotify.music': 'Spotify',
  'com.netflix.mediaclient': 'Netflix',
  'com.discord': 'Discord',
  'org.telegram.messenger': 'Telegram',
};

/** True when the string looks like an Android application ID, not a display name. */
export function looksLikeAndroidPackage(value: string | null | undefined): boolean {
  if (!value) return false;
  const v = value.trim();
  if (!v.includes('.')) return false;
  // e.g. com.miui.screenshot — at least two segments, mostly lowercase identifiers
  return /^[a-zA-Z][\w]*(\.[a-zA-Z][\w]*)+$/.test(v);
}

/** Turn a package id into a short readable label (last segment, title-cased). */
export function friendlyPackageLabel(packageName: string | null | undefined): string {
  if (!packageName?.trim()) return 'App';
  const pkg = packageName.trim();
  const known = KNOWN_PACKAGE_LABELS[pkg];
  if (known) return known;
  const last = pkg.split('.').pop() ?? pkg;
  if (!last) return 'App';
  const spaced = last
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/[_-]+/g, ' ')
    .trim();
  return spaced.replace(/\b\w/g, c => c.toUpperCase()) || 'App';
}

/**
 * Prefer a real app label / notification title; never return a raw package id.
 */
export function appDisplayName(opts: {
  appLabel?: string | null;
  title?: string | null;
  packageName?: string | null;
  fallback?: string;
}): string {
  const fallback = opts.fallback ?? 'App';
  const label = opts.appLabel?.trim();
  if (label && !looksLikeAndroidPackage(label)) return label;
  const title = opts.title?.trim();
  if (title && !looksLikeAndroidPackage(title)) return title;
  if (opts.packageName?.trim()) return friendlyPackageLabel(opts.packageName);
  if (label) return friendlyPackageLabel(label);
  if (title) return friendlyPackageLabel(title);
  return fallback;
}
