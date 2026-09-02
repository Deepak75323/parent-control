import {useState, type CSSProperties} from 'react';
import {chatMediaLayoutStyle} from '../../lib/chatMediaLayout';

type Props = {
  className: string;
  kind: 'image' | 'video';
  src: string;
  alt: string;
  controls?: boolean;
  autoPlay?: boolean;
  maxWidth?: number;
  maxHeight?: number;
};

export function ChatMediaPreview({
  className,
  kind,
  src,
  alt,
  controls = false,
  autoPlay = false,
  maxWidth,
  maxHeight,
}: Props) {
  const [layoutStyle, setLayoutStyle] = useState<CSSProperties | undefined>();

  function applyLayout(naturalWidth: number, naturalHeight: number) {
    setLayoutStyle(chatMediaLayoutStyle(naturalWidth, naturalHeight, maxWidth, maxHeight));
  }

  if (kind === 'video') {
    return (
      <video
        className={className}
        src={src}
        controls={controls}
        autoPlay={autoPlay}
        playsInline
        preload="metadata"
        style={layoutStyle}
        aria-label={alt}
        onLoadedMetadata={event => {
          const video = event.currentTarget;
          applyLayout(video.videoWidth, video.videoHeight);
        }}
      />
    );
  }

  return (
    <img
      className={className}
      src={src}
      alt={alt}
      style={layoutStyle}
      onLoad={event => {
        const image = event.currentTarget;
        applyLayout(image.naturalWidth, image.naturalHeight);
      }}
    />
  );
}
