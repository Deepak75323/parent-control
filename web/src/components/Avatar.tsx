import {getAvatarColor, getAvatarInitials} from '../lib/avatarUtils';

type Props = {
  name: string;
  seed?: string;
  imageUrl?: string | null;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
};

const SIZE_PX = {sm: 32, md: 40, lg: 44} as const;

export function Avatar({name, seed, imageUrl, size = 'md', className = ''}: Props) {
  const px = SIZE_PX[size];
  const colorSeed = seed ?? name;
  const initials = getAvatarInitials(name);
  const background = getAvatarColor(colorSeed);

  if (imageUrl?.trim()) {
    return (
      <img
        className={`chat-avatar chat-avatar--${size} ${className}`.trim()}
        src={imageUrl}
        alt=""
        width={px}
        height={px}
        style={{width: px, height: px}}
      />
    );
  }

  return (
    <span
      className={`chat-avatar chat-avatar--${size} chat-avatar--fallback ${className}`.trim()}
      style={{
        width: px,
        height: px,
        background,
        fontSize: Math.round(px * 0.36),
      }}
      aria-hidden>
      {initials}
    </span>
  );
}
