const KEY = 'pcp_library_progress_v1';

type CachedProgress = {
  lastPage: number;
  scrollOffset?: number;
  zoomLevel?: number;
  pageCount?: number;
  savedAt: number;
};

function readAll(): Record<string, CachedProgress> {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) {
      return {};
    }
    return JSON.parse(raw) as Record<string, CachedProgress>;
  } catch {
    return {};
  }
}

export function cacheLibraryProgress(bookId: string, progress: Omit<CachedProgress, 'savedAt'>) {
  const all = readAll();
  all[bookId] = {...progress, savedAt: Date.now()};
  try {
    localStorage.setItem(KEY, JSON.stringify(all));
  } catch {
    /* quota */
  }
}

export function readCachedLibraryProgress(bookId: string): CachedProgress | null {
  return readAll()[bookId] ?? null;
}
