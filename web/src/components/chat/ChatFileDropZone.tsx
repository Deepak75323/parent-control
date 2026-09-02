import {type DragEvent, type ReactNode, useCallback, useRef, useState} from 'react';

type Props = {
  enabled?: boolean;
  onFile: (file: File) => void;
  children: ReactNode;
};

function isFileDrag(event: DragEvent<HTMLElement>): boolean {
  return Array.from(event.dataTransfer.types).includes('Files');
}

export function ChatFileDropZone({enabled = true, onFile, children}: Props) {
  const dragDepthRef = useRef(0);
  const [dragActive, setDragActive] = useState(false);

  const resetDrag = useCallback(() => {
    dragDepthRef.current = 0;
    setDragActive(false);
  }, []);

  const handleDragEnter = useCallback(
    (event: DragEvent<HTMLDivElement>) => {
      if (!enabled || !isFileDrag(event)) {
        return;
      }
      event.preventDefault();
      dragDepthRef.current += 1;
      setDragActive(true);
    },
    [enabled],
  );

  const handleDragLeave = useCallback(
    (event: DragEvent<HTMLDivElement>) => {
      if (!enabled || !isFileDrag(event)) {
        return;
      }
      event.preventDefault();
      dragDepthRef.current = Math.max(0, dragDepthRef.current - 1);
      if (dragDepthRef.current === 0) {
        setDragActive(false);
      }
    },
    [enabled],
  );

  const handleDragOver = useCallback(
    (event: DragEvent<HTMLDivElement>) => {
      if (!enabled || !isFileDrag(event)) {
        return;
      }
      event.preventDefault();
      event.dataTransfer.dropEffect = 'copy';
    },
    [enabled],
  );

  const handleDrop = useCallback(
    (event: DragEvent<HTMLDivElement>) => {
      if (!enabled) {
        return;
      }
      event.preventDefault();
      resetDrag();
      const file = event.dataTransfer.files?.[0];
      if (file) {
        onFile(file);
      }
    },
    [enabled, onFile, resetDrag],
  );

  return (
    <div
      className={`chat-file-drop-zone${dragActive ? ' chat-file-drop-zone--active' : ''}`}
      onDragEnter={handleDragEnter}
      onDragLeave={handleDragLeave}
      onDragOver={handleDragOver}
      onDrop={handleDrop}>
      {children}
      {dragActive ? (
        <div className="chat-file-drop-overlay" role="status" aria-live="polite">
          <div className="chat-file-drop-overlay-inner">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
              <path d="M12 16V4m0 0L8 8m4-4 4 4M4 14v4a2 2 0 002 2h12a2 2 0 002-2v-4" />
            </svg>
            <p>Drop image or video to attach</p>
          </div>
        </div>
      ) : null}
    </div>
  );
}
