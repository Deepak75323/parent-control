import {Avatar} from '../Avatar';
import type {MentionCandidate} from '../../lib/chatMentions';

type Props = {
  candidates: MentionCandidate[];
  activeIndex: number;
  onSelect: (candidate: MentionCandidate) => void;
};

export function MentionAutocomplete({candidates, activeIndex, onSelect}: Props) {
  if (candidates.length === 0) {
    return null;
  }

  return (
    <div className="chat-mention-menu" role="listbox" aria-label="Mention a parent">
      {candidates.map((candidate, index) => (
        <button
          key={candidate.userId}
          type="button"
          role="option"
          aria-selected={index === activeIndex}
          className={[
            'chat-mention-item',
            index === activeIndex ? 'chat-mention-item--active' : '',
          ]
            .filter(Boolean)
            .join(' ')}
          onMouseDown={event => {
            event.preventDefault();
            onSelect(candidate);
          }}>
          <Avatar name={candidate.displayName} seed={candidate.userId} size="sm" />
          <span className="chat-mention-item-text">
            <span className="chat-mention-item-name">{candidate.displayName}</span>
            <span className="chat-mention-item-token">{candidate.token}</span>
          </span>
        </button>
      ))}
    </div>
  );
}
