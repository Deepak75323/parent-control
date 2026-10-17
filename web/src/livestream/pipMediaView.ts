export type PipMediaView =
  | {kind: 'hidden'}
  | {kind: 'own'; label: string}
  | {kind: 'opened'; label: string}
  | {kind: 'unlock'; label: string}
  | {kind: 'view'; label: string}
  | {kind: 'show'};

type PipMediaInput = {
  messageType?: 'TEXT' | 'IMAGE' | 'VIDEO';
  ownMessage?: boolean;
  attachmentConsumed?: boolean;
  canViewAttachment?: boolean;
  deleted?: boolean;
  unlocked: boolean;
  showing: boolean;
};

function mediaWord(messageType: 'IMAGE' | 'VIDEO'): string {
  return messageType === 'VIDEO' ? 'video' : 'photo';
}

/** What the floating chat should show for a photo or video note. */
export function pipMediaView(input: PipMediaInput): PipMediaView {
  if (input.deleted || (input.messageType !== 'IMAGE' && input.messageType !== 'VIDEO')) {
    return {kind: 'hidden'};
  }
  const word = mediaWord(input.messageType);
  if (input.ownMessage) {
    const state = input.attachmentConsumed ? 'Opened' : 'Locked until recipient unlocks';
    return {kind: 'own', label: `View once ${word} · ${state}`};
  }
  if (input.showing && input.unlocked) {
    return {kind: 'show'};
  }
  if (input.attachmentConsumed) {
    return {kind: 'opened', label: `View once ${word} · Opened`};
  }
  if (!input.canViewAttachment) {
    return {kind: 'hidden'};
  }
  if (!input.unlocked) {
    return {kind: 'unlock', label: `Unlock decrypt to view ${word} once`};
  }
  return {kind: 'view', label: `Tap to view ${word} once`};
}
