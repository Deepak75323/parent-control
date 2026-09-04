import {useEffect, useRef, useState} from 'react';
import type {WebCameraFacing} from '../lib/cameraViewerLayout';
import {
  inferStreamFacingFromCvo,
  paintVideoFrameToCanvas,
  resolveFrontCameraForPaint,
  videoFrameDisplaySize,
} from '../lib/cameraFramePaint';
import {createMediaStreamTrackProcessor} from '../lib/mediaStreamTrackProcessor';
import type {ViewerRotateDeg} from '../lib/viewerVideoRotate';
import {viewerVideoRotateStyle, viewerZoomLayerStyle} from '../lib/viewerVideoRotate';

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
  const rotateLayerRef = useRef<HTMLDivElement>(null);
  const [wrapSize, setWrapSize] = useState({w: 0, h: 0});
  const onDimsRef = useRef(onVideoDimensions);
  onDimsRef.current = onVideoDimensions;
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
      const box = rotateLayerRef.current ?? container;
      const w = Math.max(1, Math.floor(box.clientWidth || box.getBoundingClientRect().width));
      const h = Math.max(1, Math.floor(box.clientHeight || box.getBoundingClientRect().height));
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
      // Viewer rotate is CSS on the wrap (so <video> fallback rotates too). Canvas only applies CVO.
      paintVideoFrameToCanvas(
        ctx,
        frame,
        canvas.width,
        canvas.height,
        objectFitRef.current,
        0,
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
      (rotateLayerRef.current ?? container).appendChild(video);
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

    const ro = new ResizeObserver(entries => {
      const rect = entries[0]?.contentRect;
      if (rect) {
        setWrapSize({w: Math.round(rect.width), h: Math.round(rect.height)});
      }
      resizeCanvas();
    });
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

  const rotateStyle = viewerVideoRotateStyle(
    viewerRotate,
    wrapSize.w || 640,
    wrapSize.h || 400,
    objectFit,
    1,
  );

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
      <div ref={rotateLayerRef} style={rotateStyle}>
        <canvas ref={canvasRef} className={canvasClassName} aria-label="Live camera" />
      </div>
    </div>
  );
}
