import {describe, expect, it, beforeEach} from 'vitest';
import {
  cacheLibraryProgress,
  readCachedLibraryProgress,
} from './libraryProgressCache';

const store = new Map<string, string>();

const localStorageMock = {
  getItem: (k: string) => store.get(k) ?? null,
  setItem: (k: string, v: string) => {
    store.set(k, v);
  },
  clear: () => store.clear(),
  removeItem: (k: string) => {
    store.delete(k);
  },
};

Object.defineProperty(globalThis, 'localStorage', {value: localStorageMock});

describe('libraryProgressCache', () => {
  beforeEach(() => {
    store.clear();
  });

  it('stores and reads last page per book', () => {
    cacheLibraryProgress('book-a', {lastPage: 127, zoomLevel: 1.2});
    cacheLibraryProgress('book-b', {lastPage: 3});

    expect(readCachedLibraryProgress('book-a')?.lastPage).toBe(127);
    expect(readCachedLibraryProgress('book-a')?.zoomLevel).toBe(1.2);
    expect(readCachedLibraryProgress('book-b')?.lastPage).toBe(3);
    expect(readCachedLibraryProgress('missing')).toBeNull();
  });
});
