import {useEffect, useRef} from 'react';
import {
  paintVideoFrameToCanvas,
  videoFrameDisplaySize,
} from '../lib/cameraFramePaint';
import {createMediaStreamTrackProcessor} from '../lib/mediaStreamTrackProcessor';
import type {ViewerRotateDeg} from '../lib/viewerVideoRotate';

type VideoFrameWithCvo = VideoFrame & {rotation?: number};

type Props = {
  stream: MediaStream | null;
  objectFit?: 'cover' | 'contain';
  className?: string;
  canvasClassName?: string;
  viewerRotate?: ViewerRotateDeg;
  /** Front lens streams carry mirrored-preview CVO and need compensation. */
  frontCamera?: boolean;
  onVideoDimensions?: (width: number, height: number) => void;
};

/**
 * Camera viewer via WebCodecs canvas — VideoFrame.drawImage applies RTP CVO reliably.
 * The <video> tag applies CVO inconsistently in Chrome (sideways, upside-down, or correct).
 */
export function CameraFrameViewer({
  stream,
  objectFit = 'cover',
  className,
  canvasClassName,
  viewerRotate = 0,
  frontCamera = true,
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
  const frontCameraRef = useRef(frontCamera);
  frontCameraRef.current = frontCamera;

  useEffect(() => {
    const canvas = canvasRef.current;
    const container = containerRef.current;
    if (!stream || !canvas || !container) {
      return;
    }

    const track = stream.getVideoTracks()[0];
    if (!track) {
      return;
    }

    let cancelled = false;
    let reader: ReadableStreamDefaultReader<VideoFrameWithCvo> | null = null;

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
      paintVideoFrameToCanvas(
        ctx,
        frame,
        canvas.width,
        canvas.height,
        objectFitRef.current,
        viewerRotateRef.current,
        frontCameraRef.current,
      );
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

    const pump = async () => {
      const processor = createMediaStreamTrackProcessor(track);
      if (!processor) {
        return;
      }
      reader = processor.readable.getReader();
      resizeCanvas();

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
    };

    const fallbackToVideo = () => {
      canvas.style.display = 'none';
      const video = document.createElement('video');
      video.className = canvasClassName ?? '';
      video.autoplay = true;
      video.playsInline = true;
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
      void video.play().then(sync).catch(() => undefined);
      return () => {
        video.removeEventListener('loadedmetadata', sync);
        video.srcObject = null;
        video.remove();
        canvas.style.display = '';
      };
    };

    let cleanupVideo: (() => void) | undefined;
    if (!createMediaStreamTrackProcessor(track)) {
      cleanupVideo = fallbackToVideo();
    } else {
      void pump();
    }
    const ro = new ResizeObserver(resizeCanvas);
    ro.observe(container);

    return () => {
      cancelled = true;
      ro.disconnect();
      cleanupVideo?.();
      void reader?.cancel().catch(() => undefined);
    };
  }, [stream, canvasClassName]);

  return (
    <div ref={containerRef} className={className} style={{position: 'absolute', inset: 0}}>
      <canvas ref={canvasRef} className={canvasClassName} aria-label="Live camera" />
    </div>
  );
}
