import {useEffect, useRef} from 'react';
import type {WebCameraFacing} from '../lib/cameraViewerLayout';
import {
  inferStreamFacingFromCvo,
  paintVideoFrameToCanvas,
  resolveFrontCameraForPaint,
  videoFrameDisplaySize,
} from '../lib/cameraFramePaint';
import {createMediaStreamTrackProcessor} from '../lib/mediaStreamTrackProcessor';
import type {ViewerRotateDeg} from '../lib/viewerVideoRotate';
import {viewerZoomLayerStyle} from '../lib/viewerVideoRotate';

type VideoFrameWithCvo = VideoFrame & {rotation?: number};

const CANVAS_FRAME_TIMEOUT_MS = 2500;

type Props = {
  stream: MediaStream | null;
  objectFit?: 'cover' | 'contain';
  className?: string;
  canvasClassName?: string;
  viewerRotate?: ViewerRotateDeg;
  /** Viewer-only pinch/button zoom (CSS scale on the canvas layer). */
  viewZoomScale?: number;
  /** UI hint when CVO cannot distinguish front vs back (e.g. landscape tilt). */
  facingHint?: WebCameraFacing;
  onFacingInferred?: (facing: WebCameraFacing) => void;
  onVideoDimensions?: (width: number, height: number) => void;
};

/** Mobile browsers often expose MediaStreamTrackProcessor but never deliver frames. */
function preferVideoElement(): boolean {
  if (typeof navigator === 'undefined') {
    return false;
  }
  return /iPhone|iPad|iPod|Android/i.test(navigator.userAgent);
}

/**
 * Camera viewer via WebCodecs canvas — VideoFrame.drawImage applies RTP CVO reliably.
 * Falls back to <video> on mobile or when the canvas path receives no frames.
 */
export function CameraFrameViewer({
  stream,
  objectFit = 'cover',
  className,
  canvasClassName,
  viewerRotate = 0,
  viewZoomScale = 1,
  facingHint = 'user',
  onFacingInferred,
  onVideoDimensions,
}: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const onDimsRef = useRef(onVideoDimensions);
  onDimsRef.current = onVideoDimensions;
  const viewerRotateRef = useRef(viewerRotate);
  viewerRotateRef.current = viewerRotate;
  const objectFitRef = useRef(objectFit);
  objectFitRef.current = objectFit;
  const facingHintRef = useRef(facingHint);
  facingHintRef.current = facingHint;
  const onFacingInferredRef = useRef(onFacingInferred);
  onFacingInferredRef.current = onFacingInferred;
  const lastReportedFacingRef = useRef<WebCameraFacing | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    const container = containerRef.current;
    if (!stream || !canvas || !container) {
      return;
    }

    let cancelled = false;
    let reader: ReadableStreamDefaultReader<VideoFrameWithCvo> | null = null;
    let cleanupVideo: (() => void) | undefined;
    let frameTimeout: ReturnType<typeof setTimeout> | undefined;
    let paintedFrame = false;

    const resizeCanvas = () => {
      const rect = container.getBoundingClientRect();
      const w = Math.max(1, Math.floor(rect.width));
      const h = Math.max(1, Math.floor(rect.height));
      if (canvas.width !== w || canvas.height !== h) {
        canvas.width = w;
        canvas.height = h;
      }
    };

    const paintFrame = (frame: VideoFrameWithCvo) => {
      const ctx = canvas.getContext('2d');
      if (!ctx) {
        return;
      }
      resizeCanvas();
      const frontCamera = resolveFrontCameraForPaint(frame, facingHintRef.current);
      const inferred = inferStreamFacingFromCvo(
        frame.rotation,
        frame.codedWidth,
        frame.codedHeight,
      );
      if (inferred != null && inferred !== lastReportedFacingRef.current) {
        lastReportedFacingRef.current = inferred;
        onFacingInferredRef.current?.(inferred);
      }
      paintVideoFrameToCanvas(
        ctx,
        frame,
        canvas.width,
        canvas.height,
        objectFitRef.current,
        viewerRotateRef.current,
        frontCamera,
      );
      paintedFrame = true;
      if (frameTimeout) {
        clearTimeout(frameTimeout);
        frameTimeout = undefined;
      }
      const {width, height} = videoFrameDisplaySize({
        codedWidth: frame.codedWidth,
        codedHeight: frame.codedHeight,
        displayWidth: frame.displayWidth,
        displayHeight: frame.displayHeight,
        rotation: frame.rotation ?? 0,
      });
      if (width > 0 && height > 0) {
        onDimsRef.current?.(width, height);
      }
    };

    const mountVideoFallback = () => {
      if (cancelled || cleanupVideo) {
        return;
      }
      cancelled = true;
      void reader?.cancel().catch(() => undefined);
      reader = null;
      cleanupVideo = fallbackToVideo();
    };

    const fallbackToVideo = () => {
      canvas.style.display = 'none';
      const video = document.createElement('video');
      video.className = canvasClassName ?? '';
      video.autoplay = true;
      video.playsInline = true;
      video.setAttribute('playsinline', 'true');
      video.muted = true;
      video.style.position = 'absolute';
      video.style.inset = '0';
      video.style.width = '100%';
      video.style.height = '100%';
      video.style.objectFit = objectFitRef.current;
      video.srcObject = stream;
      container.appendChild(video);
      const sync = () => {
        if (video.videoWidth > 0 && video.videoHeight > 0) {
          onDimsRef.current?.(video.videoWidth, video.videoHeight);
        }
      };
      video.addEventListener('loadedmetadata', sync);
      video.addEventListener('resize', sync);
      void video.play().then(sync).catch(() => undefined);
      return () => {
        video.removeEventListener('loadedmetadata', sync);
        video.removeEventListener('resize', sync);
        video.srcObject = null;
        video.remove();
        canvas.style.display = '';
      };
    };

    const startCanvasPump = (track: MediaStreamTrack) => {
      const processor = createMediaStreamTrackProcessor(track);
      if (!processor) {
        cleanupVideo = fallbackToVideo();
        return;
      }

      frameTimeout = setTimeout(() => {
        if (!paintedFrame) {
          mountVideoFallback();
        }
      }, CANVAS_FRAME_TIMEOUT_MS);

      const pump = async () => {
        reader = processor.readable.getReader();
        resizeCanvas();
        try {
          while (!cancelled) {
            const {done, value: frame} = await reader.read();
            if (done || cancelled) {
              frame?.close();
              break;
            }
            if (!frame) {
              continue;
            }
            paintFrame(frame);
            frame.close();
          }
        } catch {
          if (!paintedFrame && !cancelled) {
            mountVideoFallback();
          }
        }
      };

      void pump();
    };

    const startForTrack = (track: MediaStreamTrack | undefined) => {
      if (cancelled || cleanupVideo) {
        return;
      }
      if (!track) {
        return;
      }
      if (preferVideoElement()) {
        cleanupVideo = fallbackToVideo();
        return;
      }
      startCanvasPump(track);
    };

    startForTrack(stream.getVideoTracks()[0]);

    const onAddTrack = (event: MediaStreamTrackEvent) => {
      if (event.track.kind === 'video' && !paintedFrame && !cleanupVideo) {
        startForTrack(event.track);
      }
    };
    stream.addEventListener('addtrack', onAddTrack);

    const ro = new ResizeObserver(resizeCanvas);
    ro.observe(container);

    return () => {
      cancelled = true;
      if (frameTimeout) {
        clearTimeout(frameTimeout);
      }
      stream.removeEventListener('addtrack', onAddTrack);
      ro.disconnect();
      cleanupVideo?.();
      void reader?.cancel().catch(() => undefined);
    };
  }, [stream, canvasClassName]);

  return (
    <div
      ref={containerRef}
      className={className}
      style={{
        position: 'absolute',
        inset: 0,
        transformOrigin: 'center center',
        ...viewerZoomLayerStyle(viewZoomScale),
      }}>
      <canvas ref={canvasRef} className={canvasClassName} aria-label="Live camera" />
    </div>
  );
}
