import type {ChatMessageReactionSummary} from '../../types/api';
import {reactionTooltip} from '../../lib/chatReactionEmojis';

type Props = {
  reactions?: ChatMessageReactionSummary[];
  onToggle: (emoji: string) => void;
};

export function MessageReactions({reactions = [], onToggle}: Props) {
  if (reactions.length === 0) {
    return null;
  }

  return (
    <div className="chat-message-reactions" aria-label="Message reactions">
      {reactions.map(reaction => (
        <button
          key={reaction.emoji}
          type="button"
          className={`chat-message-reaction${reaction.reactedByMe ? ' chat-message-reaction--mine' : ''}`}
          title={reactionTooltip(reaction.reactorLabels)}
          aria-label={`${reaction.emoji} ${reaction.count}${reaction.reactedByMe ? ', your reaction' : ''}`}
          onClick={() => onToggle(reaction.emoji)}>
          <span className="chat-message-reaction-emoji" aria-hidden>
            {reaction.emoji}
          </span>
          {reaction.count > 1 ? (
            <span className="chat-message-reaction-count">{reaction.count}</span>
          ) : null}
        </button>
      ))}
    </div>
  );
}
