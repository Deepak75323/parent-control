import {splitMessageText} from '../../lib/chatMentions';

type Props = {
  text: string;
  mentionTokens: string[];
  edited?: boolean;
};

export function ChatMessageText({text, mentionTokens, edited = false}: Props) {
  const parts = splitMessageText(text, mentionTokens);

  return (
    <p className="chat-message-body">
      {parts.map((part, index) =>
        part.type === 'mention' ? (
          <span
            key={`${part.value}-${index}`}
            className={part.known ? 'chat-message-mention' : 'chat-message-mention chat-message-mention--unknown'}>
            {part.value}
          </span>
        ) : (
          <span key={`text-${index}`}>{part.value}</span>
        ),
      )}
      {edited ? <span className="chat-message-edited"> (edited)</span> : null}
    </p>
  );
}
