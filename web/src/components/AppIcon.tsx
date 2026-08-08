import {appDisplayName} from '../lib/appDisplayName';

const AVATAR_COLORS = ['#5b5ceb', '#632ce5', '#4140d1', '#7c4dff', '#2f2bc1'];

type Props = {
  label: string;
  iconBase64?: string | null;
  index?: number;
  size?: number;
};

export function AppIcon({label, iconBase64, index = 0, size = 36}: Props) {
  const letter = (label.trim().charAt(0) || '?').toUpperCase();
  const bg = AVATAR_COLORS[index % AVATAR_COLORS.length];
  const radius = Math.round(size * 0.28);

  if (iconBase64) {
    const src = iconBase64.startsWith('data:')
      ? iconBase64
      : `data:image/png;base64,${iconBase64}`;
    return (
      <img
        className="app-icon"
        src={src}
        alt=""
        width={size}
        height={size}
        style={{width: size, height: size, borderRadius: radius}}
      />
    );
  }

  return (
    <span
      className="app-icon app-icon-fallback"
      style={{
        width: size,
        height: size,
        borderRadius: radius,
        background: bg,
        fontSize: Math.round(size * 0.42),
      }}
      aria-hidden>
      {letter}
    </span>
  );
}

export function resolveAppLabel(opts: {
  appLabel?: string | null;
  packageName?: string | null;
  title?: string | null;
}): string {
  return appDisplayName(opts);
}
