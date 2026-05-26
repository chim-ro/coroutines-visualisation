import React from 'react';
import { QuizState } from '../hooks/useQuizMode';
import { SURFACE_COLOR, BORDER_COLOR, ACCENT_COLOR, TEXT_COLOR, TEXT_DIM } from '../utils/colors';

interface Props {
  quizState: QuizState;
  onSubmitAnswer: (index: number) => void;
  onContinue: () => void;
}

export const QuizPanel: React.FC<Props> = ({ quizState, onSubmitAnswer, onContinue }) => {
  const { phase, currentQuestion, selectedAnswer, isCorrect, score } = quizState;

  if (phase === 'idle' || !currentQuestion) return null;

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (phase === 'answered' && e.key === 'Enter') {
      onContinue();
    }
  };

  return (
    <div
      style={{
        position: 'absolute',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        pointerEvents: 'none',
        zIndex: 10,
      }}
      onKeyDown={handleKeyDown}
    >
      <div
        style={{
          background: SURFACE_COLOR,
          border: `1px solid ${BORDER_COLOR}`,
          borderRadius: 12,
          padding: 24,
          maxWidth: 420,
          width: '90%',
          pointerEvents: 'auto',
          boxShadow: '0 8px 32px rgba(0,0,0,0.5)',
        }}
      >
        {/* Score */}
        <div style={{ fontSize: 11, color: TEXT_DIM, marginBottom: 12, textAlign: 'right' }}>
          Score: {score.correct}/{score.total}
        </div>

        {/* Question */}
        <div style={{ fontSize: 15, color: TEXT_COLOR, fontWeight: 600, marginBottom: 16 }}>
          {currentQuestion.prompt}
        </div>

        {/* Options */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {currentQuestion.options.map((option, i) => {
            let bg = 'transparent';
            let borderColor = BORDER_COLOR;

            if (phase === 'answered') {
              if (i === currentQuestion.correctIndex) {
                bg = '#9ece6a22';
                borderColor = '#9ece6a';
              } else if (i === selectedAnswer && !isCorrect) {
                bg = '#f7768e22';
                borderColor = '#f7768e';
              }
            } else if (i === selectedAnswer) {
              borderColor = ACCENT_COLOR;
            }

            return (
              <button
                key={i}
                onClick={() => phase === 'questioning' && onSubmitAnswer(i)}
                style={{
                  background: bg,
                  border: `1px solid ${borderColor}`,
                  borderRadius: 8,
                  color: TEXT_COLOR,
                  padding: '10px 14px',
                  fontSize: 13,
                  fontFamily: 'inherit',
                  textAlign: 'left',
                  cursor: phase === 'questioning' ? 'pointer' : 'default',
                  transition: 'border-color 0.15s, background 0.15s',
                }}
              >
                {option}
              </button>
            );
          })}
        </div>

        {/* Result + Continue */}
        {phase === 'answered' && (
          <div style={{ marginTop: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{
              fontSize: 13,
              fontWeight: 600,
              color: isCorrect ? '#9ece6a' : '#f7768e',
            }}>
              {isCorrect ? 'Correct!' : 'Incorrect'}
            </span>
            <button
              onClick={onContinue}
              autoFocus
              style={{
                background: ACCENT_COLOR,
                border: 'none',
                borderRadius: 6,
                color: '#1a1b26',
                padding: '8px 20px',
                fontSize: 13,
                fontWeight: 600,
                fontFamily: 'inherit',
                cursor: 'pointer',
              }}
            >
              Continue →
            </button>
          </div>
        )}
      </div>
    </div>
  );
};
