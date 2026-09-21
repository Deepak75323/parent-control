import {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {Link, useParams} from 'react-router-dom';
import {Document, Page, pdfjs} from 'react-pdf';
import 'react-pdf/dist/Page/AnnotationLayer.css';
import 'react-pdf/dist/Page/TextLayer.css';
import {libraryApi} from '../api/services';
import {
  cacheLibraryProgress,
  readCachedLibraryProgress,
} from '../lib/libraryProgressCache';
import type {HighlightColor, LibraryBookmark, LibraryBook, LibraryHighlight} from '../types/api';

pdfjs.GlobalWorkerOptions.workerSrc = new URL(
  'pdfjs-dist/build/pdf.worker.min.mjs',
  import.meta.url,
).toString();

const HIGHLIGHT_COLORS: {id: HighlightColor; label: string; swatch: string}[] = [
  {id: 'YELLOW', label: 'Yellow', swatch: '#f5d76e'},
  {id: 'BLUE', label: 'Blue', swatch: '#7eb6ff'},
  {id: 'GREEN', label: 'Green', swatch: '#7ddea2'},
  {id: 'RED', label: 'Red', swatch: '#f0a0a0'},
];

type SelectionToolbar = {
  x: number;
  y: number;
  text: string;
  pageNumber: number;
};

export function LibraryReaderPage() {
  const {bookId = ''} = useParams();
  const [book, setBook] = useState<LibraryBook | null>(null);
  const [pdfUrl, setPdfUrl] = useState<string | null>(null);
  const [pageNumber, setPageNumber] = useState(1);
  const [numPages, setNumPages] = useState<number | null>(null);
  const [zoom, setZoom] = useState(1.1);
  const [error, setError] = useState<string | null>(null);
  const [loadingMeta, setLoadingMeta] = useState(true);
  const [loadingPdf, setLoadingPdf] = useState(false);
  const [pdfProgress, setPdfProgress] = useState<number | null>(null);
  const [highlights, setHighlights] = useState<LibraryHighlight[]>([]);
  const [bookmarks, setBookmarks] = useState<LibraryBookmark[]>([]);
  const [panel, setPanel] = useState<'highlights' | 'bookmarks' | null>('highlights');
  const [searchQuery, setSearchQuery] = useState('');
  const [searchMatches, setSearchMatches] = useState<{page: number; index: number}[]>([]);
  const [searchCursor, setSearchCursor] = useState(0);
  const [selection, setSelection] = useState<SelectionToolbar | null>(null);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const saveTimer = useRef<number | null>(null);
  const pageRef = useRef(pageNumber);
  const zoomRef = useRef(zoom);
  const numPagesRef = useRef(numPages);

  pageRef.current = pageNumber;
  zoomRef.current = zoom;
  numPagesRef.current = numPages;

  const persistProgress = useCallback(
    async (immediate = false) => {
      if (!bookId) {
        return;
      }
      const payload = {
        lastPage: pageRef.current,
        pageCount: numPagesRef.current ?? undefined,
        zoomLevel: zoomRef.current,
      };
      cacheLibraryProgress(bookId, payload);
      const run = async () => {
        try {
          await libraryApi.saveProgress(bookId, payload);
        } catch {
          /* local cache kept */
        }
      };
      if (immediate) {
        if (saveTimer.current) {
          window.clearTimeout(saveTimer.current);
        }
        await run();
        return;
      }
      if (saveTimer.current) {
        window.clearTimeout(saveTimer.current);
      }
      saveTimer.current = window.setTimeout(() => void run(), 800);
    },
    [bookId],
  );

  useEffect(() => {
    let revoked: string | null = null;
    let cancelled = false;

    (async () => {
      setLoadingMeta(true);
      setLoadingPdf(false);
      setPdfProgress(null);
      setError(null);
      setPdfUrl(null);
      try {
        const [{data: bookRes}, hl, bm] = await Promise.all([
          libraryApi.get(bookId),
          libraryApi.listHighlights(bookId),
          libraryApi.listBookmarks(bookId),
        ]);
        if (cancelled) {
          return;
        }
        setBook(bookRes.data);
        setHighlights(hl.data.data ?? []);
        setBookmarks(bm.data.data ?? []);
        const cached = readCachedLibraryProgress(bookId);
        const serverPage = bookRes.data?.progress?.lastPage ?? 1;
        const cachedNewer =
          cached &&
          (!bookRes.data?.progress?.lastViewedAt ||
            cached.savedAt > new Date(bookRes.data.progress.lastViewedAt).getTime());
        setPageNumber(cachedNewer && cached ? cached.lastPage : serverPage);
        if (cachedNewer && cached?.zoomLevel) {
          setZoom(cached.zoomLevel);
        } else if (bookRes.data?.progress?.zoomLevel) {
          setZoom(bookRes.data.progress.zoomLevel);
        }
        setLoadingMeta(false);

        setLoadingPdf(true);
        const url = await libraryApi.fetchPdfObjectUrl(bookId, (loaded, total) => {
          if (cancelled) {
            return;
          }
          if (total && total > 0) {
            setPdfProgress(Math.min(99, Math.round((loaded / total) * 100)));
          }
        });
        if (cancelled) {
          URL.revokeObjectURL(url);
          return;
        }
        revoked = url;
        setPdfUrl(url);
        setPdfProgress(100);
      } catch (e) {
        if (!cancelled) {
          const detail = e instanceof Error && e.message ? e.message : '';
          setError(detail && detail !== 'Failed to load PDF' ? detail : 'Could not open this book');
        }
      } finally {
        if (!cancelled) {
          setLoadingMeta(false);
          setLoadingPdf(false);
        }
      }
    })();

    return () => {
      cancelled = true;
      if (revoked) {
        URL.revokeObjectURL(revoked);
      }
    };
  }, [bookId]);

  useEffect(() => {
    return () => {
      void persistProgress(true);
    };
  }, [persistProgress]);

  useEffect(() => {
    const onVis = () => {
      if (document.visibilityState === 'hidden') {
        void persistProgress(true);
      }
    };
    document.addEventListener('visibilitychange', onVis);
    window.addEventListener('pagehide', onVis);
    return () => {
      document.removeEventListener('visibilitychange', onVis);
      window.removeEventListener('pagehide', onVis);
    };
  }, [persistProgress]);

  useEffect(() => {
    void persistProgress(false);
  }, [pageNumber, zoom, persistProgress]);

  const pageHighlights = useMemo(
    () => highlights.filter(h => h.pageNumber === pageNumber),
    [highlights, pageNumber],
  );

  const goTo = (page: number) => {
    if (!numPages) {
      setPageNumber(Math.max(1, page));
      return;
    }
    setPageNumber(Math.min(numPages, Math.max(1, page)));
    setSelection(null);
  };

  const onDocumentLoad = (info: {numPages: number}) => {
    setNumPages(info.numPages);
    if (book && book.pageCount !== info.numPages) {
      void libraryApi.update(book.id, {pageCount: info.numPages}).catch(() => undefined);
    }
  };

  const onTextSelect = () => {
    const sel = window.getSelection();
    const text = sel?.toString().trim() ?? '';
    if (!text || text.length < 2) {
      setSelection(null);
      return;
    }
    const range = sel?.getRangeAt(0);
    const rect = range?.getBoundingClientRect();
    if (!rect) {
      return;
    }
    setSelection({
      x: rect.left + rect.width / 2,
      y: rect.top - 8,
      text,
      pageNumber,
    });
  };

  const createHighlight = async (color: HighlightColor) => {
    if (!selection || !bookId) {
      return;
    }
    try {
      const positionData = JSON.stringify({
        text: selection.text,
        page: selection.pageNumber,
      });
      const {data} = await libraryApi.createHighlight(bookId, {
        pageNumber: selection.pageNumber,
        selectedText: selection.text,
        color,
        positionData,
      });
      if (data.data) {
        setHighlights(prev => [...prev, data.data]);
      }
      setSelection(null);
      window.getSelection()?.removeAllRanges();
    } catch {
      setError('Could not save highlight');
    }
  };

  const deleteHighlight = async (id: string) => {
    try {
      await libraryApi.deleteHighlight(bookId, id);
      setHighlights(prev => prev.filter(h => h.id !== id));
    } catch {
      setError('Could not delete highlight');
    }
  };

  const addBookmark = async () => {
    try {
      const {data} = await libraryApi.createBookmark(bookId, {
        pageNumber,
        label: `Page ${pageNumber}`,
      });
      if (data.data) {
        setBookmarks(prev => [...prev, data.data].sort((a, b) => a.pageNumber - b.pageNumber));
      }
    } catch {
      setError('Could not save bookmark');
    }
  };

  const deleteBookmark = async (id: string) => {
    try {
      await libraryApi.deleteBookmark(bookId, id);
      setBookmarks(prev => prev.filter(b => b.id !== id));
    } catch {
      setError('Could not delete bookmark');
    }
  };

  const runSearch = async () => {
    const q = searchQuery.trim().toLowerCase();
    if (!q || !pdfUrl) {
      setSearchMatches([]);
      return;
    }
    try {
      const pdf = await pdfjs.getDocument({url: pdfUrl}).promise;
      const matches: {page: number; index: number}[] = [];
      for (let p = 1; p <= pdf.numPages; p++) {
        const page = await pdf.getPage(p);
        const content = await page.getTextContent();
        const text = content.items
          .map(item => ('str' in item ? item.str : ''))
          .join(' ')
          .toLowerCase();
        let from = 0;
        let idx = 0;
        while (true) {
          const found = text.indexOf(q, from);
          if (found < 0) {
            break;
          }
          matches.push({page: p, index: idx++});
          from = found + q.length;
        }
      }
      setSearchMatches(matches);
      setSearchCursor(0);
      if (matches[0]) {
        goTo(matches[0].page);
      }
    } catch {
      setError('Search failed');
    }
  };

  const jumpSearch = (dir: 1 | -1) => {
    if (!searchMatches.length) {
      return;
    }
    const next = (searchCursor + dir + searchMatches.length) % searchMatches.length;
    setSearchCursor(next);
    goTo(searchMatches[next].page);
  };

  if (loadingMeta) {
    return (
      <div className="page">
        <p className="muted">Opening book…</p>
      </div>
    );
  }

  if (error && !book) {
    return (
      <div className="page">
        <div className="error">{error}</div>
        <Link className="btn ghost" to="/library">
          ← Library
        </Link>
      </div>
    );
  }

  return (
    <div className={`library-reader${sidebarOpen ? ' sidebar-open' : ''}`}>
      <header className="library-reader-toolbar">
        <Link className="btn ghost sm" to="/library" onClick={() => void persistProgress(true)}>
          ← Library
        </Link>
        <div className="library-reader-title">{book?.title ?? 'Book'}</div>
        <div className="library-reader-page">
          Page {pageNumber}
          {numPages ? ` / ${numPages}` : ''}
        </div>
        <div className="library-reader-search">
          <input
            type="search"
            placeholder="Search in this book…"
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            onKeyDown={e => {
              if (e.key === 'Enter') {
                void runSearch();
              }
            }}
          />
          {searchMatches.length > 0 ? (
            <span className="tiny muted">
              {searchCursor + 1} / {searchMatches.length}
            </span>
          ) : null}
          <button type="button" className="btn ghost sm" onClick={() => jumpSearch(-1)}>
            ↑
          </button>
          <button type="button" className="btn ghost sm" onClick={() => jumpSearch(1)}>
            ↓
          </button>
        </div>
        <button type="button" className="btn ghost sm" onClick={() => void addBookmark()}>
          Bookmark
        </button>
        <button
          type="button"
          className="btn ghost sm library-reader-panel-toggle"
          onClick={() => setSidebarOpen(o => !o)}>
          {sidebarOpen ? 'Hide panel' : 'Highlights'}
        </button>
      </header>

      {error ? <div className="error library-reader-error">{error}</div> : null}
      {loadingPdf ? (
        <div className="library-reader-download card">
          <p className="muted">
            Downloading PDF{pdfProgress != null ? `… ${pdfProgress}%` : '…'}
          </p>
          <div className="library-progress-track">
            <div
              className="library-progress-fill"
              style={{width: `${pdfProgress ?? 8}%`}}
            />
          </div>
          <p className="tiny muted">Large books can take a minute on slower connections.</p>
        </div>
      ) : null}

      <div className="library-reader-body">
        <main className="library-reader-main" onMouseUp={onTextSelect}>
          {pdfUrl ? (
            <Document
              file={pdfUrl}
              onLoadSuccess={onDocumentLoad}
              onLoadError={() => setError('This PDF could not be rendered')}
              loading={<p className="muted">Rendering…</p>}>
              <Page
                pageNumber={pageNumber}
                scale={zoom}
                renderTextLayer
                renderAnnotationLayer
                loading={<p className="muted">Loading page…</p>}
              />
            </Document>
          ) : !loadingPdf ? (
            <p className="muted">PDF is not available.</p>
          ) : null}
          {pageHighlights.length > 0 ? (
            <div className="library-page-highlight-hint muted tiny">
              {pageHighlights.length} highlight{pageHighlights.length === 1 ? '' : 's'} on this page
            </div>
          ) : null}
        </main>

        <aside className={`library-reader-aside${sidebarOpen ? ' open' : ''}`}>
          <div className="library-aside-tabs">
            <button
              type="button"
              className={panel === 'highlights' ? 'active' : ''}
              onClick={() => setPanel('highlights')}>
              Highlights
            </button>
            <button
              type="button"
              className={panel === 'bookmarks' ? 'active' : ''}
              onClick={() => setPanel('bookmarks')}>
              Bookmarks
            </button>
          </div>
          {panel === 'highlights' ? (
            <ul className="library-aside-list">
              {highlights.length === 0 ? (
                <li className="muted tiny">Select text in the PDF to highlight.</li>
              ) : (
                highlights.map(h => (
                  <li key={h.id}>
                    <button type="button" className="library-aside-item" onClick={() => goTo(h.pageNumber)}>
                      <span className={`library-swatch ${h.color.toLowerCase()}`} />
                      <span>
                        <strong>Page {h.pageNumber}</strong>
                        <span className="muted">{h.selectedText.slice(0, 120)}</span>
                      </span>
                    </button>
                    <button
                      type="button"
                      className="btn ghost sm"
                      onClick={() => void deleteHighlight(h.id)}>
                      Delete
                    </button>
                  </li>
                ))
              )}
            </ul>
          ) : (
            <ul className="library-aside-list">
              {bookmarks.length === 0 ? (
                <li className="muted tiny">No bookmarks yet.</li>
              ) : (
                bookmarks.map(b => (
                  <li key={b.id}>
                    <button type="button" className="library-aside-item" onClick={() => goTo(b.pageNumber)}>
                      🔖 Page {b.pageNumber}
                      {b.label ? ` — ${b.label}` : ''}
                    </button>
                    <button
                      type="button"
                      className="btn ghost sm"
                      onClick={() => void deleteBookmark(b.id)}>
                      Delete
                    </button>
                  </li>
                ))
              )}
            </ul>
          )}
        </aside>
      </div>

      <footer className="library-reader-footer">
        <button type="button" className="btn ghost sm" disabled={pageNumber <= 1} onClick={() => goTo(pageNumber - 1)}>
          Previous
        </button>
        <button type="button" className="btn ghost sm" onClick={() => setZoom(z => Math.max(0.5, z - 0.1))}>
          −
        </button>
        <span className="tiny">{Math.round(zoom * 100)}%</span>
        <button type="button" className="btn ghost sm" onClick={() => setZoom(z => Math.min(2.5, z + 0.1))}>
          +
        </button>
        <button type="button" className="btn ghost sm" onClick={() => setZoom(1.1)}>
          Fit
        </button>
        <button
          type="button"
          className="btn ghost sm"
          disabled={!numPages || pageNumber >= numPages}
          onClick={() => goTo(pageNumber + 1)}>
          Next
        </button>
      </footer>

      {selection ? (
        <div
          className="library-selection-toolbar"
          style={{left: selection.x, top: selection.y}}>
          {HIGHLIGHT_COLORS.map(c => (
            <button
              key={c.id}
              type="button"
              title={c.label}
              style={{background: c.swatch}}
              onClick={() => void createHighlight(c.id)}
            />
          ))}
        </div>
      ) : null}
    </div>
  );
}
