import {createPortal} from 'react-dom';
import {type CSSProperties, type ReactNode, type RefObject, useEffect, useState} from 'react';

type Props = {
  open: boolean;
  anchorRef: RefObject<HTMLElement | null>;
  className?: string;
  children: ReactNode;
};

/** Renders compose popovers above the anchor without clipping from overflow:hidden parents. */
export function ComposeAnchorPortal({open, anchorRef, className, children}: Props) {
  const [style, setStyle] = useState<CSSProperties>({});

  useEffect(() => {
    if (!open) {
      return;
    }

    function updatePosition() {
      const anchor = anchorRef.current;
      if (!anchor) {
        return;
      }
      const rect = anchor.getBoundingClientRect();
      const horizontalPadding = 8;
      const width = Math.min(Math.max(rect.width, 280), window.innerWidth - horizontalPadding * 2);
      const left = Math.min(
        Math.max(horizontalPadding, rect.left),
        window.innerWidth - width - horizontalPadding,
      );
      setStyle({
        position: 'fixed',
        left,
        width,
        bottom: Math.max(8, window.innerHeight - rect.top + 8),
        zIndex: 10060,
      });
    }

    updatePosition();
    window.addEventListener('resize', updatePosition);
    window.addEventListener('scroll', updatePosition, true);
    return () => {
      window.removeEventListener('resize', updatePosition);
      window.removeEventListener('scroll', updatePosition, true);
    };
  }, [open, anchorRef]);

  if (!open) {
    return null;
  }

  return createPortal(
    <div className={className} style={style}>
      {children}
    </div>,
    document.body,
  );
}
