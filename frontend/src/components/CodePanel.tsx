import React, { useState } from 'react';
import { SURFACE_COLOR, BORDER_COLOR, TEXT_COLOR, TEXT_DIM, ACCENT_COLOR } from '../utils/colors';

const KEYWORDS = [
  'fun', 'val', 'var', 'suspend', 'return', 'throw', 'try', 'catch', 'finally',
  'if', 'else', 'while', 'for', 'when', 'import', 'class', 'object', 'interface',
];

const COROUTINE_CALLS = [
  'runBlocking', 'launch', 'async', 'delay', 'coroutineScope', 'supervisorScope',
  'withContext', 'await', 'cancel', 'join',
];

const STRING_COLOR = '#9ece6a';
const COMMENT_COLOR = '#565f89';
const KEYWORD_COLOR = '#bb9af7';
const COROUTINE_COLOR = '#7aa2f7';
const NUMBER_COLOR = '#ff9e64';

function highlightLine(line: string): React.ReactNode[] {
  const parts: React.ReactNode[] = [];
  let remaining = line;
  let key = 0;

  // Handle line comments
  const commentIdx = remaining.indexOf('//');
  let commentPart = '';
  if (commentIdx !== -1) {
    commentPart = remaining.slice(commentIdx);
    remaining = remaining.slice(0, commentIdx);
  }

  // Tokenize non-comment part
  const tokenRegex = /("(?:[^"\\]|\\.)*")|(\b\d+L?\b)|(\b[a-zA-Z_]\w*\b)|([^\s\w"]+|\s+)/g;
  let match: RegExpExecArray | null;

  while ((match = tokenRegex.exec(remaining)) !== null) {
    const token = match[0];

    if (match[1]) {
      // String literal
      parts.push(<span key={key++} style={{ color: STRING_COLOR }}>{token}</span>);
    } else if (match[2]) {
      // Number
      parts.push(<span key={key++} style={{ color: NUMBER_COLOR }}>{token}</span>);
    } else if (match[3]) {
      // Identifier
      if (KEYWORDS.includes(token)) {
        parts.push(<span key={key++} style={{ color: KEYWORD_COLOR }}>{token}</span>);
      } else if (COROUTINE_CALLS.includes(token)) {
        parts.push(<span key={key++} style={{ color: COROUTINE_COLOR, fontWeight: 600 }}>{token}</span>);
      } else if (token === 'println' || token === 'print') {
        parts.push(<span key={key++} style={{ color: '#e0af68' }}>{token}</span>);
      } else if (token[0] === token[0].toUpperCase() && /^[A-Z]/.test(token)) {
        // Type names
        parts.push(<span key={key++} style={{ color: '#2ac3de' }}>{token}</span>);
      } else {
        parts.push(<span key={key++}>{token}</span>);
      }
    } else {
      parts.push(<span key={key++}>{token}</span>);
    }
  }

  if (commentPart) {
    parts.push(<span key={key++} style={{ color: COMMENT_COLOR, fontStyle: 'italic' }}>{commentPart}</span>);
  }

  return parts;
}

interface CodePanelProps {
  kotlinCode: string;
}

export const CodePanel: React.FC<CodePanelProps> = ({ kotlinCode }) => {
  const [collapsed, setCollapsed] = useState(true);
  const [copied, setCopied] = useState(false);

  if (!kotlinCode) return null;

  const lines = kotlinCode.split('\n');

  const handleCopy = () => {
    navigator.clipboard.writeText(kotlinCode).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  };

  return (
    <div style={{
      borderTop: `1px solid ${BORDER_COLOR}`,
      background: SURFACE_COLOR,
    }}>
      <button
        onClick={() => setCollapsed(prev => !prev)}
        style={{
          width: '100%',
          padding: '6px 12px',
          background: 'none',
          border: 'none',
          color: ACCENT_COLOR,
          fontSize: 12,
          fontWeight: 600,
          cursor: 'pointer',
          textAlign: 'left',
          display: 'flex',
          alignItems: 'center',
          gap: 6,
        }}
      >
        <span style={{
          display: 'inline-block',
          transform: collapsed ? 'rotate(-90deg)' : 'rotate(0deg)',
          transition: 'transform 0.15s',
          fontSize: 10,
        }}>
          &#9660;
        </span>
        {collapsed ? 'Show Kotlin Code' : 'Hide Kotlin Code'}
      </button>

      {!collapsed && (
        <div style={{
          padding: '0 12px 10px',
          maxHeight: 260,
          overflowY: 'auto',
        }}>
          <div style={{ position: 'relative' }}>
            <button
              onClick={handleCopy}
              style={{
                position: 'absolute',
                top: 6,
                right: 6,
                background: copied ? '#9ece6a22' : `${BORDER_COLOR}`,
                border: `1px solid ${copied ? '#9ece6a44' : BORDER_COLOR}`,
                color: copied ? '#9ece6a' : TEXT_DIM,
                borderRadius: 4,
                padding: '3px 8px',
                fontSize: 11,
                cursor: 'pointer',
                zIndex: 1,
                transition: 'all 0.15s',
              }}
            >
              {copied ? 'Copied!' : 'Copy'}
            </button>
            <pre style={{
              margin: 0,
              padding: 10,
              background: '#1a1b26',
              borderRadius: 6,
              fontSize: 12,
              lineHeight: 1.5,
              fontFamily: "'JetBrains Mono', 'Fira Code', 'Cascadia Code', monospace",
              color: TEXT_COLOR,
              overflowX: 'auto',
            }}>
              {lines.map((line, i) => (
                <div key={i} style={{ display: 'flex' }}>
                  <span style={{
                    color: TEXT_DIM,
                    userSelect: 'none',
                    width: 32,
                    textAlign: 'right',
                    paddingRight: 12,
                    flexShrink: 0,
                  }}>
                    {i + 1}
                  </span>
                  <span>{highlightLine(line)}</span>
                </div>
              ))}
            </pre>
          </div>
        </div>
      )}
    </div>
  );
};
