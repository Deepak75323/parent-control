import {useState} from 'react';
import {isNewsItem, type LearningQuestion} from '../../lib/learningQuestions';

type Props = {
  question: LearningQuestion;
};

function categorySubtitle(question: LearningQuestion): string {
  switch (question.category) {
    case 'CSAT':
      return 'UPSC · CSAT practice';
    case 'BPSC':
      return 'BPSC practice';
    case 'Gov Exam English':
      return 'Government exam · English';
    case 'Gov Exam Maths':
      return 'Government exam · Maths';
    case 'JEE Maths':
      return 'JEE Advanced · Maths';
    case 'JEE Physics':
      return 'JEE Advanced · Physics';
    case 'Current Affairs':
      return 'Current affairs';
    case 'AI News':
      return 'Daily AI news';
    case 'Maths News':
      return 'Maths & science news';
    case 'Technology News':
      return 'Technology news';
    default:
      return question.category;
  }
}

/** Notebook card at the end of the thread — practice question or news brief. */
export function LearningChallengeCard({question}: Props) {
  const [revealed, setRevealed] = useState(false);
  const news = isNewsItem(question);

  return (
    <section
      className={`chat-learning-card${news ? ' chat-learning-card--news' : ''}`}
      aria-label={news ? 'Learning news brief' : 'Learning challenge'}>
      <header className="chat-learning-card-head">
        <span className="chat-learning-card-emoji" aria-hidden>
          {news ? '📰' : '🧠'}
        </span>
        <h3 className="chat-learning-card-heading">
          {news ? 'Today’s Brief' : 'Learning Challenge'}
        </h3>
      </header>
      <p className="chat-learning-card-category">{categorySubtitle(question)}</p>
      <p className="chat-learning-card-question">{question.question}</p>
      {question.source ? (
        <p className="chat-learning-card-source">Source: {question.source}</p>
      ) : null}
      {!news && question.answer ? (
        <>
          {revealed ? (
            <p className="chat-learning-card-answer" role="status">
              <span className="chat-learning-card-answer-label">Answer:</span> {question.answer}
            </p>
          ) : null}
          <button
            type="button"
            className="chat-learning-card-toggle"
            onClick={() => setRevealed(v => !v)}
            aria-expanded={revealed}>
            {revealed ? 'Hide Answer' : 'Show Answer'}
          </button>
        </>
      ) : null}
    </section>
  );
}
