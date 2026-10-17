import {isNewsItem, type LearningQuestion} from '../../lib/learningQuestions';

type Props = {
  question: LearningQuestion;
};

/** Compact challenge / news nested inside a notebook message bubble (UI-only). */
export function MessageLearningChallenge({question}: Props) {
  const news = isNewsItem(question);
  return (
    <aside
      className={`chat-learning-inline${news ? ' chat-learning-inline--news' : ''}`}
      aria-label={`${question.category} ${news ? 'news' : 'challenge'}`}>
      <p className="chat-learning-inline-label">{question.label}</p>
      <p className="chat-learning-inline-question">{question.question}</p>
      {question.source ? (
        <p className="chat-learning-inline-source">{question.source}</p>
      ) : null}
    </aside>
  );
}
