import {useCallback, useEffect, useMemo, useRef, useState, type DragEvent} from 'react';
import {Link} from 'react-router-dom';
import {libraryApi} from '../api/services';
import type {LibraryBook} from '../types/api';
import {useConfirmDialog} from '../hooks/useConfirmDialog';

type Filter = 'all' | 'reading' | 'completed' | 'not_started';

const MAX_PDF_BYTES = 100 * 1024 * 1024;

function formatBytes(n: number): string {
  if (n < 1024) {
    return `${n} B`;
  }
  if (n < 1024 * 1024) {
    return `${(n / 1024).toFixed(1)} KB`;
  }
  return `${(n / (1024 * 1024)).toFixed(1)} MB`;
}

function formatRelative(iso?: string | null): string {
  if (!iso) {
    return 'Never opened';
  }
  const ms = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(ms / 60000);
  if (mins < 1) {
    return 'Just now';
  }
  if (mins < 60) {
    return `${mins} minute${mins === 1 ? '' : 's'} ago`;
  }
  const hours = Math.floor(mins / 60);
  if (hours < 24) {
    return `${hours} hour${hours === 1 ? '' : 's'} ago`;
  }
  const days = Math.floor(hours / 24);
  return `${days} day${days === 1 ? '' : 's'} ago`;
}

function bookFilter(book: LibraryBook, filter: Filter): boolean {
  const pct = book.progress?.progressPercent ?? 0;
  const opened = Boolean(book.progress?.lastViewedAt);
  if (filter === 'all') {
    return true;
  }
  if (filter === 'completed') {
    return pct >= 100 || Boolean(book.progress?.completedAt);
  }
  if (filter === 'not_started') {
    return !opened || pct <= 0;
  }
  return opened && pct > 0 && pct < 100;
}

export function LibraryPage() {
  const {confirm, dialog: confirmDialog} = useConfirmDialog();
  const fileRef = useRef<HTMLInputElement>(null);
  const [books, setBooks] = useState<LibraryBook[]>([]);
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<Filter>('all');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [uploadPct, setUploadPct] = useState<number | null>(null);
  const [menuOpenId, setMenuOpenId] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const {data} = await libraryApi.list(query.trim() || undefined);
      setBooks(data.data ?? []);
      setError(null);
    } catch {
      setError('Failed to load library');
    } finally {
      setLoading(false);
    }
  }, [query]);

  useEffect(() => {
    setLoading(true);
    const t = window.setTimeout(() => void load(), query ? 250 : 0);
    return () => window.clearTimeout(t);
  }, [load, query]);

  const visible = useMemo(
    () => books.filter(b => bookFilter(b, filter)),
    [books, filter],
  );

  const onPickFiles = async (files: FileList | null) => {
    if (!files?.length) {
      return;
    }
    setError(null);
    setUploading(true);
    setUploadPct(0);
    let uploaded = 0;
    try {
      const list = Array.from(files);
      for (let i = 0; i < list.length; i++) {
        const file = list[i];
        if (file.type !== 'application/pdf' && !file.name.toLowerCase().endsWith('.pdf')) {
          setError(`${file.name}: only PDF files are allowed`);
          continue;
        }
        if (file.size > MAX_PDF_BYTES) {
          setError(`${file.name}: exceeds 100 MB limit`);
          continue;
        }
        setUploadPct(Math.round(((i + 0.5) / list.length) * 100));
        const {data} = await libraryApi.upload(file);
        const created = data.data;
        if (created) {
          setBooks(prev => [created, ...prev.filter(b => b.id !== created.id)]);
          setFilter('all');
        }
        uploaded += 1;
        setUploadPct(Math.round(((i + 1) / list.length) * 100));
      }
      if (uploaded > 0) {
        await load();
      }
    } catch (e: unknown) {
      const msg =
        (e as {response?: {data?: {message?: string}}})?.response?.data?.message ||
        (e instanceof Error ? e.message : '') ||
        'Upload failed';
      setError(msg);
    } finally {
      setUploading(false);
      setUploadPct(null);
      if (fileRef.current) {
        fileRef.current.value = '';
      }
    }
  };

  const deleteBook = async (book: LibraryBook) => {
    setMenuOpenId(null);
    const ok = await confirm({
      title: 'Delete book?',
      message: `Remove “${book.title}” from your library? This cannot be undone.`,
      confirmLabel: 'Delete',
      danger: true,
    });
    if (!ok) {
      return;
    }
    try {
      await libraryApi.delete(book.id);
      await load();
    } catch {
      setError('Could not delete book');
    }
  };

  const shareToggle = async (book: LibraryBook) => {
    setMenuOpenId(null);
    if (!book.ownedByMe) {
      return;
    }
    try {
      await libraryApi.update(book.id, {
        visibility: book.visibility === 'SHARED' ? 'PRIVATE' : 'SHARED',
      });
      await load();
    } catch {
      setError('Could not update sharing');
    }
  };

  const onDrop = (event: DragEvent) => {
    event.preventDefault();
    void onPickFiles(event.dataTransfer.files);
  };

  return (
    <div
      className="page library-page"
      onDragOver={event => event.preventDefault()}
      onDrop={onDrop}>
      <input
        id="library-pdf-input"
        ref={fileRef}
        className="library-file-input"
        type="file"
        accept="application/pdf,.pdf"
        multiple
        disabled={uploading}
        onChange={e => {
          void onPickFiles(e.target.files);
          e.target.value = '';
        }}
      />
      <div className="page-header">
        <div>
          <h1>Library</h1>
          <p className="muted">Your books and shared reading</p>
        </div>
        <label htmlFor="library-pdf-input" className={`btn primary${uploading ? ' is-disabled' : ''}`}>
          {uploading ? 'Uploading…' : '+ Add PDF'}
        </label>
      </div>

      {error ? <div className="error">{error}</div> : null}
      {uploadPct != null ? (
        <div className="library-upload-progress card">
          <div className="muted">Uploading… {uploadPct}%</div>
          <div className="library-progress-track">
            <div className="library-progress-fill" style={{width: `${uploadPct}%`}} />
          </div>
        </div>
      ) : null}

      <div className="library-toolbar">
        <input
          className="library-search"
          type="search"
          placeholder="Search books…"
          value={query}
          onChange={e => setQuery(e.target.value)}
        />
        <div className="library-filters">
          {(
            [
              ['all', 'All'],
              ['reading', 'Reading'],
              ['completed', 'Completed'],
              ['not_started', 'Not Started'],
            ] as const
          ).map(([id, label]) => (
            <button
              key={id}
              type="button"
              className={`library-filter${filter === id ? ' active' : ''}`}
              onClick={() => setFilter(id)}>
              {label}
            </button>
          ))}
        </div>
      </div>

      {loading ? (
        <div className="card">
          <p className="muted">Loading library…</p>
        </div>
      ) : visible.length === 0 && books.length === 0 ? (
        <div className="card library-empty">
          <div className="library-empty-icon" aria-hidden>
            📚
          </div>
          <h2>Your Library</h2>
          <p className="muted">
            Upload PDFs and keep all your important books and study material in one place.
          </p>
          <label htmlFor="library-pdf-input" className="btn primary">
            + Add PDF
          </label>
          <p className="tiny muted">Or drop a PDF anywhere on this page.</p>
          <p className="tiny muted">
            Your reading position and highlights will be saved automatically.
          </p>
        </div>
      ) : visible.length === 0 ? (
        <div className="card">
          <p className="muted">No books match this filter.</p>
        </div>
      ) : (
        <div className="library-grid">
          {visible.map(book => {
            const pct = Math.round(book.progress?.progressPercent ?? 0);
            const lastPage = book.progress?.lastPage ?? 1;
            const pages = book.pageCount;
            const started = Boolean(book.progress?.lastViewedAt);
            return (
              <article key={book.id} className="library-card card">
                <div className="library-card-top">
                  <div className="library-card-icon" aria-hidden>
                    📘
                  </div>
                  <div className="library-card-meta">
                    <h2 className="library-card-title">{book.title}</h2>
                    <div className="tiny muted">
                      {formatBytes(book.fileSizeBytes)}
                      {pages ? ` · ${pages} pages` : ''}
                      {book.visibility === 'SHARED' ? ' · Shared' : ''}
                      {!book.ownedByMe ? ' · From a friend' : ''}
                    </div>
                  </div>
                  {book.ownedByMe ? (
                    <div className="library-card-menu-wrap">
                      <button
                        type="button"
                        className="btn ghost sm library-more"
                        aria-label="More"
                        onClick={() =>
                          setMenuOpenId(menuOpenId === book.id ? null : book.id)
                        }>
                        ⋮
                      </button>
                      {menuOpenId === book.id ? (
                        <div className="library-card-menu">
                          <button type="button" onClick={() => void shareToggle(book)}>
                            {book.visibility === 'SHARED'
                              ? 'Make private'
                              : 'Share with friends'}
                          </button>
                          <button
                            type="button"
                            className="danger"
                            onClick={() => void deleteBook(book)}>
                            Delete
                          </button>
                        </div>
                      ) : null}
                    </div>
                  ) : null}
                </div>

                {started && pages ? (
                  <p className="library-page-line">
                    Page {lastPage} / {pages}
                  </p>
                ) : pages ? (
                  <p className="library-page-line muted">{pages} pages</p>
                ) : (
                  <p className="library-page-line muted">PDF ready</p>
                )}

                {started ? (
                  <>
                    <div className="library-progress-track">
                      <div
                        className="library-progress-fill"
                        style={{width: `${Math.min(100, pct)}%`}}
                      />
                    </div>
                    <div className="row-between tiny muted">
                      <span>{pct}% complete</span>
                      <span>Last opened {formatRelative(book.progress?.lastViewedAt)}</span>
                    </div>
                  </>
                ) : (
                  <p className="muted tiny">Not started</p>
                )}

                <div className="library-card-actions">
                  <Link
                    className="btn primary sm"
                    to={`/library/${book.id}`}
                    onClick={() => setMenuOpenId(null)}>
                    {started ? 'Continue Reading' : 'Start Reading'}
                  </Link>
                </div>
              </article>
            );
          })}
        </div>
      )}

      {confirmDialog}
    </div>
  );
}
