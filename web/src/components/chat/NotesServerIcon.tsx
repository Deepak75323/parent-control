import {useId} from 'react';

type IconProps = {
  size?: number;
  className?: string;
};

type GlyphProps = {
  name: string;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
};

const GLYPH_ICON_PX = {sm: 22, md: 24, lg: 28} as const;

export function isNotesServerName(name: string | null | undefined): boolean {
  return (name ?? '').trim().toLowerCase() === 'notes';
}

/** Coral spiral notebook — crisp at 32–48px. */
export function NotesServerIcon({size = 32, className = ''}: IconProps) {
  const uid = useId().replace(/:/g, '');
  const coverId = `notesCover-${uid}`;
  const spineId = `notesSpine-${uid}`;
  const shadowId = `notesShadow-${uid}`;

  return (
    <svg
      className={`notes-server-icon ${className}`.trim()}
      width={size}
      height={size}
      viewBox="0 0 40 40"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden>
      <defs>
        <linearGradient id={coverId} x1="12" y1="4" x2="34" y2="36" gradientUnits="userSpaceOnUse">
          <stop stopColor="#F07868" />
          <stop offset="1" stopColor="#E45A4A" />
        </linearGradient>
        <linearGradient id={spineId} x1="30" y1="8" x2="36" y2="32" gradientUnits="userSpaceOnUse">
          <stop stopColor="#6B7AE8" />
          <stop offset="1" stopColor="#5B4FEF" />
        </linearGradient>
        <filter id={shadowId} x="0" y="2" width="40" height="38" filterUnits="userSpaceOnUse">
          <feDropShadow dx="0" dy="1.2" stdDeviation="1.1" floodColor="#2A2D4A" floodOpacity="0.18" />
        </filter>
      </defs>

      {/* Soft page stack behind cover */}
      <rect x="13" y="7.5" width="20" height="26" rx="3.5" fill="#F4F2FF" />
      <rect x="14.5" y="6.5" width="19" height="26" rx="3.5" fill="#E8E6FA" />

      {/* Main coral cover */}
      <g filter={`url(#${shadowId})`}>
        <rect x="10" y="5.5" width="20.5" height="27" rx="3.8" fill={`url(#${coverId})`} />
      </g>

      {/* Purple/blue page edge accent */}
      <path
        d="M29.2 8.2c.9.4 1.5 1.4 1.5 2.5v18.6c0 1.1-.6 2.1-1.5 2.5V8.2Z"
        fill={`url(#${spineId})`}
        opacity="0.95"
      />

      {/* Title label + writing lines */}
      <rect x="14.2" y="11.2" width="12.2" height="7.2" rx="1.4" fill="#FFFCF9" opacity="0.95" />
      <path d="M16 13.6h8.6M16 15.8h7.2" stroke="#C9C4D8" strokeWidth="1" strokeLinecap="round" />
      <path
        d="M15.5 22.2h10.5M15.5 24.6h9M15.5 27h7.5"
        stroke="#FFFFFF"
        strokeWidth="1.15"
        strokeLinecap="round"
        opacity="0.7"
      />

      {/* Accent dots on right edge */}
      <circle cx="31.2" cy="14" r="1.05" fill="#4058D8" />
      <circle cx="31.2" cy="18.2" r="1.05" fill="#6B8CFF" />
      <circle cx="31.2" cy="22.4" r="1.05" fill="#5B4FEF" />

      {/* Small bookmark tab */}
      <path d="M30.4 9.2h2.4c.5 0 .9.4.9.9v3.2l-1.65-1.1L30.4 13.3V9.2Z" fill="#F0C14A" />

      {/* Spiral rings */}
      <g stroke="#3A3D52" strokeWidth="1.55" strokeLinecap="round" fill="none">
        <path d="M8.2 10.2c1.6-.9 3.2-.9 4.8 0" />
        <path d="M8.2 14.6c1.6-.9 3.2-.9 4.8 0" />
        <path d="M8.2 19c1.6-.9 3.2-.9 4.8 0" />
        <path d="M8.2 23.4c1.6-.9 3.2-.9 4.8 0" />
        <path d="M8.2 27.8c1.6-.9 3.2-.9 4.8 0" />
      </g>
      <g fill="#2E3145">
        <circle cx="8.2" cy="10.2" r="1.15" />
        <circle cx="8.2" cy="14.6" r="1.15" />
        <circle cx="8.2" cy="19" r="1.15" />
        <circle cx="8.2" cy="23.4" r="1.15" />
        <circle cx="8.2" cy="27.8" r="1.15" />
      </g>
    </svg>
  );
}

/** Drop-in replacement for letter glyphs on the Notes server only. */
export function ServerGlyph({name, size = 'md', className = ''}: GlyphProps) {
  const notes = isNotesServerName(name);
  const large = size === 'lg';
  const classes = [
    'chat-server-glyph',
    large ? 'chat-server-glyph--lg' : '',
    notes ? 'chat-server-glyph--notes' : '',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <span className={classes} aria-hidden>
      {notes ? <NotesServerIcon size={GLYPH_ICON_PX[size]} /> : (name.slice(0, 1).toUpperCase() || '?')}
    </span>
  );
}
